#!/usr/bin/env python3
"""
Derleyici olmadan yakalanabilen Kotlin hatalarini tarar.

Bu depoda Android SDK bulunmadigi icin Compose katmani derlenemiyor; bu betik
o katmanda gozden kacan birkac somut hata sinifini yakalar:

  1. Dengesiz ayraclar (yama sirasinda blok kaybi)
  1b. Dengesi tutan ama yuvalanmasi kaymis dosya: ust duzey bir bildirim
      baska bir fonksiyonun icinde kalmis (kapanis parantezi yanlis yere
      konunca oluyor; toplam sayi tuttugu icin 1. madde yakalamiyor)
  2. Tanimsiz tur ve fonksiyon adlari (silinmis sinif, unutulmus import)
  3. JVM imza cakismasi ("var zoom" ile "fun setZoom" ayni imzayi uretir)

Kullanim: python3 tools/lint_kotlin.py android/app/src/main/java
"""
import glob
import os
import re
import sys

STDLIB = set("""
Int Long Float Double Boolean String Char Byte Short Unit Any Array List Map Set Nothing
MutableList MutableMap MutableSet ArrayList HashMap HashSet Pair Triple Exception RuntimeException
SecurityException IllegalStateException IllegalArgumentException Math System Locale Date Comparator
Charsets ByteArray FloatArray IntArray ShortArray DoubleArray LongArray BooleanArray CharArray
StringBuilder SimpleDateFormat NumberFormat Collator Volatile Synchronized JvmStatic Composable
Override Suppress OptIn Deprecated Immutable Stable Builder BigTextStyle RequestPermission
Color Modifier Offset Rect Size Dp TextStyle FontWeight Path Brush Stroke Fill Canvas Text Box Row
Column Spacer Switch Build Bundle Intent Context Notification Service Manifest R PendingIntent
ByteBuffer ByteOrder JSONObject JSONArray File
""".split())


TOP_LEVEL = re.compile(
    r'^(?:private |internal |public )?'
    r'(?:suspend |inline |operator |data |sealed |abstract |open |value )*'
    r'(?:fun|val|var|class|object|interface|enum class|typealias)\s'
)


def strip_comments_and_strings(src: str) -> str:
    text = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r'//[^\n]*', '', text)
    return re.sub(r'/\*(?:.|\n)*?\*/', '', text)


def collect_declarations(files):
    declared = set()
    for path in files:
        src = open(path, encoding='utf-8').read()
        patterns = (
            r'^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:private |internal |public |abstract |sealed |open '
            r'|data |value )*(?:class|object|interface|enum class|annotation class)\s+(\w+)',
            r'^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:private |internal |public |suspend |inline '
            r'|operator )*fun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)\s*\(',
            r'^\s*(?:private |internal |public )?(?:const )?va[lr]\s+(\w+)\s*[:=]',
            r'^\s*typealias\s+(\w+)',
        )
        for pattern in patterns:
            declared.update(m.group(1) for m in re.finditer(pattern, src, re.M))
        declared.update(
            m.group(1)
            for m in re.finditer(r'^\s*([A-Z][A-Z0-9_]*)\s*(?:\([^()]*\))?\s*[,;]', src, re.M)
        )
    return declared


def main(root: str) -> int:
    files = sorted(glob.glob(os.path.join(root, '**/*.kt'), recursive=True))
    if not files:
        print(f"{root} altinda Kotlin dosyasi yok")
        return 1

    declared = collect_declarations(files)
    issues = []

    for path in files:
        src = open(path, encoding='utf-8').read()
        body = strip_comments_and_strings(src)

        for opener, closer in (('{', '}'), ('(', ')'), ('[', ']')):
            delta = body.count(opener) - body.count(closer)
            if delta:
                issues.append(f"{path}: dengesiz {opener}{closer} ({delta:+d})")

        # Girintisiz baslayan bir bildirim ancak sifir derinlikte olabilir.
        # Kapanis parantezi yanlis fonksiyonun sonuna konunca toplam sayi
        # tutuyor ama aradaki butun fonksiyonlar bir ustekinin icine gomuluyor.
        depth = 0
        for number, line in enumerate(body.split('\n'), 1):
            opened = depth
            for ch in line:
                if ch == '{':
                    depth += 1
                elif ch == '}':
                    depth -= 1
            if TOP_LEVEL.match(line) and opened != 0:
                issues.append(
                    f"{path}:{number}: ust duzey bildirim {opened} blok icinde "
                    f"kalmis - kapanis parantezi yanlis yerde"
                )

        imported = {
            (m.group(2) or m.group(1).split('.')[-1])
            for m in re.finditer(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?', src, re.M)
        }
        used = set(re.findall(r'<\s*([A-Z]\w+)\s*[,>]', body))
        used |= set(re.findall(r':\s*([A-Z]\w+)[\s<>?,)=]', body))
        # Nitelenmis erisimde (Foo.Bar()) ic ad ayrica import edilmez.
        used |= set(re.findall(r'(?<![\w.])([A-Z]\w+)\s*\(', body))
        # Uzanti fonksiyonunun alicisi: "fun Projection.pathOf(...)". Bir tur
        # adi degistirilirken en kolay gozden kacan yer burasi ve tarama uzun
        # sure bunu hic gormuyordu.
        used |= set(re.findall(r'\bfun\s+(?:<[^>]+>\s*)?([A-Z]\w+)\.\w+\s*[(<]', body))
        # "is Foo" ve "x as Foo" da tur adidir.
        used |= set(re.findall(r'\b(?:is|as)\s+([A-Z]\w+)', body))
        for name in sorted(used - imported - declared - STDLIB):
            issues.append(f"{path}: '{name}' tanimli degil ve import edilmemis")

        # JVM imza cakismasi yalnizca gorunur ozelliklerde olur; private
        # ozellikler icin Kotlin erisimci uretmez.
        public_vars = {
            m.group(1) for m in re.finditer(r'^\s*(?:internal |public )?var\s+(\w+)', body, re.M)
        }
        for m in re.finditer(r'^\s*(?:internal |public )?fun\s+set([A-Z]\w*)\s*\(', body, re.M):
            name = m.group(1)[0].lower() + m.group(1)[1:]
            if name in public_vars:
                issues.append(
                    f"{path}: 'var {name}' ile 'fun set{m.group(1)}' JVM'de ayni imzayi uretebilir"
                )

    if issues:
        print('\n'.join(issues))
        return 1
    print("tarama temiz")
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv[1] if len(sys.argv) > 1 else 'android/app/src/main/java'))
