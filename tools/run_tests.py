#!/usr/bin/env python3
"""Saf mantik katmanini gercek Kotlin derleyicisiyle derler ve testleri kosar.

Android SDK gerektirmez. `./gradlew test` ayni testleri kosar ama SDK olmayan
bir makinede Gradle daha ilk adimda duruyor; burada gereken tek sey Maven
Central'dan indirilen birkac jar ve bir JDK.

    python3 tools/run_tests.py

Derleyici ve JUnit jar'lari `.kotlin-toolchain/` altina indirilip SHA-1 ile
dogrulanir; sonraki kosularda ag'a hic cikilmaz. `--offline` ag'i tamamen
kapatir, `--keep-going` derleme uyarilarini de yazdirir.

Derlenen dosyalar: `app/src/main` icinde `android.`/`androidx.` ice aktarmasi
olmayan her sey (geometri, rota, durum turevleri, bicimlendirme, ikili varlik
ayristiricilari, bilet, gunluk bicimi, pencere kamerasi, sayac cekirdegi) ve
`app/src/test` icindeki her sey. Compose ve servis katmani burada derlenemez:
androidx yapitlarinin tek dagitim adresi Google'in kendi Maven deposu.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "android" / "app" / "src"
TOOLCHAIN = REPO / ".kotlin-toolchain"
CENTRAL = "https://repo1.maven.org/maven2"

KOTLIN = "2.0.21"
COROUTINES = "1.9.0"

# (maven yolu, yerel ad). Surumler android/app/build.gradle.kts ile ayni.
ARTIFACTS = [
    (f"org/jetbrains/kotlin/kotlin-compiler/{KOTLIN}/kotlin-compiler-{KOTLIN}.jar", "kotlin-compiler.jar"),
    (f"org/jetbrains/kotlin/kotlin-stdlib/{KOTLIN}/kotlin-stdlib-{KOTLIN}.jar", "kotlin-stdlib.jar"),
    (f"org/jetbrains/kotlin/kotlin-script-runtime/{KOTLIN}/kotlin-script-runtime-{KOTLIN}.jar", "kotlin-script-runtime.jar"),
    (f"org/jetbrains/kotlin/kotlin-reflect/{KOTLIN}/kotlin-reflect-{KOTLIN}.jar", "kotlin-reflect.jar"),
    (f"org/jetbrains/annotations/13.0/annotations-13.0.jar", "annotations.jar"),
    (f"org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/{COROUTINES}/kotlinx-coroutines-core-jvm-{COROUTINES}.jar", "kotlinx-coroutines-core-jvm.jar"),
    ("org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar", "trove4j.jar"),
    ("junit/junit/4.13.2/junit-4.13.2.jar", "junit.jar"),
    ("org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar", "hamcrest-core.jar"),
]

# Derleyicinin kendi calismasi icin gereken jar'lar (derlenecek kodun degil).
COMPILER_JARS = [
    "kotlin-compiler.jar",
    "kotlin-stdlib.jar",
    "annotations.jar",
    "kotlinx-coroutines-core-jvm.jar",
    "trove4j.jar",
]

# `-kotlin-home` bu adlari bekliyor; boylece stdlib siniflari otomatik gelir.
KOTLIN_HOME_JARS = ["kotlin-stdlib.jar", "kotlin-script-runtime.jar", "kotlin-reflect.jar"]

ANDROID_IMPORT = re.compile(r"^import\s+(android|androidx)\.", re.MULTILINE)


def log(message: str) -> None:
    print(message, flush=True)


def sha1_of(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=300) as response:
        return response.read()


def download(url: str, target: Path, attempts: int = 8) -> None:
    """Parca parca diske yazar; kesilirse kalinan yerden devam eder.

    Derleyici jar'i 60 MB ve aradaki katmanlar bagi yarida birakabiliyor.
    Tehlikeli olan tarafi: kesilme cogu zaman hata olarak gelmiyor, akis
    sessizce bitiyor ve elde kisa bir dosya kaliyor. Bu yuzden her turda
    beklenen boy ile karsilastirilip Range ile kalinan yerden devam ediliyor.
    """
    part = target.with_suffix(target.suffix + ".part")
    part.unlink(missing_ok=True)
    total = -1
    for attempt in range(1, attempts + 1):
        have = part.stat().st_size if part.exists() else 0
        if total >= 0 and have == total:
            break
        request = urllib.request.Request(url)
        if have:
            request.add_header("Range", f"bytes={have}-")
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                # Sunucu Range'i yok saydiysa bastan yaziyoruz.
                resumed = have > 0 and response.status == 206
                if not resumed:
                    have = 0
                if total < 0:
                    length = response.headers.get("Content-Length")
                    total = have + int(length) if length else -1
                with part.open("ab" if resumed else "wb") as handle:
                    while True:
                        chunk = response.read(1 << 20)
                        if not chunk:
                            break
                        handle.write(chunk)
                        have += len(chunk)
        except Exception as error:  # kesilme, zaman asimi, gecici 5xx
            if attempt == attempts:
                raise
            log(f"    kesildi ({error}); yeniden deneniyor")
            continue
        if total < 0 or have == total:
            break
        log(f"    eksik geldi ({have}/{total} bayt); kalinan yerden devam")
    else:
        raise RuntimeError(f"indirilemedi: {url}")

    if total >= 0 and part.stat().st_size != total:
        part.unlink(missing_ok=True)
        raise RuntimeError(f"eksik indirme: {url}")
    part.replace(target)


def ensure_artifact(maven_path: str, local_name: str, offline: bool) -> Path:
    target = TOOLCHAIN / local_name
    if target.exists():
        return target
    if offline:
        sys.exit(f"eksik ve --offline verildi: {target}")
    url = f"{CENTRAL}/{maven_path}"
    log(f"  indiriliyor {local_name} ...")
    download(url, target)
    expected = fetch(url + ".sha1").decode().split()[0].strip()
    actual = sha1_of(target)
    if actual != expected:
        target.unlink(missing_ok=True)
        sys.exit(f"SHA-1 tutmadi: {local_name} (beklenen {expected}, gelen {actual})")
    return target


def ensure_toolchain(offline: bool) -> None:
    TOOLCHAIN.mkdir(parents=True, exist_ok=True)
    missing = [name for _, name in ARTIFACTS if not (TOOLCHAIN / name).exists()]
    if missing:
        log(f"araç zinciri hazırlanıyor ({len(missing)} dosya) → {TOOLCHAIN.relative_to(REPO)}/")
    for maven_path, local_name in ARTIFACTS:
        ensure_artifact(maven_path, local_name, offline)

    # kotlinc'in "kotlin home" duzeni: lib/ altinda sabit adlarla.
    home_lib = TOOLCHAIN / "home" / "lib"
    home_lib.mkdir(parents=True, exist_ok=True)
    for name in KOTLIN_HOME_JARS:
        link = home_lib / name
        if not link.exists():
            shutil.copyfile(TOOLCHAIN / name, link)


def pure_sources() -> list[Path]:
    """`android.`/`androidx.` ice aktarmasi olmayan main kaynaklari."""
    files = []
    for path in sorted((SRC / "main").rglob("*.kt")):
        if not ANDROID_IMPORT.search(path.read_text(encoding="utf-8")):
            files.append(path)
    return files


def test_sources() -> list[Path]:
    return sorted((SRC / "test").rglob("*.kt"))


def java_bin(name: str) -> str:
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = Path(home) / "bin" / name
        if candidate.exists():
            return str(candidate)
    found = shutil.which(name)
    if not found:
        sys.exit(f"{name} bulunamadi; JDK 17 ya da uzeri gerekli")
    return found


def run(cmd: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True)


def compile_sources(sources: list[Path], out: Path, verbose: bool) -> None:
    classpath = os.pathsep.join(str(TOOLCHAIN / name) for name in COMPILER_JARS)
    user_classpath = os.pathsep.join(
        str(TOOLCHAIN / name) for name in ("junit.jar", "hamcrest-core.jar")
    )
    cmd = [
        java_bin("java"),
        "-cp", classpath,
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-kotlin-home", str(TOOLCHAIN / "home"),
        "-jvm-target", "17",
        "-classpath", user_classpath,
        "-nowarn",
        "-d", str(out),
    ] + [str(p) for p in sources]
    if verbose:
        cmd.remove("-nowarn")
    result = run(cmd)
    noise = [
        line for line in (result.stderr + result.stdout).splitlines()
        if line.strip() and "JAVA_TOOL_OPTIONS" not in line
    ]
    if result.returncode != 0:
        log("\n".join(noise))
        sys.exit("derleme basarisiz")
    if noise:
        log("\n".join(noise))


def test_classes(out: Path) -> list[str]:
    names = []
    for path in sorted(out.rglob("*Test.class")):
        if "$" in path.name:
            continue
        names.append(str(path.relative_to(out)).removesuffix(".class").replace(os.sep, "."))
    return names


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="hicbir sey indirme")
    parser.add_argument("--keep-going", action="store_true", help="derleme uyarilarini da yazdir")
    args = parser.parse_args()

    if not SRC.is_dir():
        sys.exit(f"kaynak agaci yok: {SRC}")

    ensure_toolchain(args.offline)

    sources = pure_sources()
    tests = test_sources()
    if not tests:
        sys.exit("test kaynagi bulunamadi")
    log(f"derleniyor: {len(sources)} saf kaynak + {len(tests)} test dosyasi")

    out = TOOLCHAIN / "classes"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    compile_sources(sources + tests, out, args.keep_going)

    classes = test_classes(out)
    if not classes:
        sys.exit("derlenmis test sinifi yok")

    runtime = os.pathsep.join(
        [str(out)] + [str(TOOLCHAIN / n) for n in ("kotlin-stdlib.jar", "junit.jar", "hamcrest-core.jar")]
    )
    result = run([java_bin("java"), "-cp", runtime, "org.junit.runner.JUnitCore"] + classes)
    for line in (result.stdout + result.stderr).splitlines():
        if "JAVA_TOOL_OPTIONS" in line:
            continue
        log(line)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
