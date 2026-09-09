/**
 * Havalimanı sözlüğü ve hazır rotalar.
 * Kayıt biçimi: KOD: [şehir, enlem, boylam]
 */

export const AP = {
  IST:["İstanbul",41.28,28.75],   ESB:["Ankara",40.13,32.99],     ADB:["İzmir",38.29,27.16],
  AYT:["Antalya",36.90,30.79],    LHR:["Londra",51.47,-0.45],     CDG:["Paris",49.01,2.55],
  FCO:["Roma",41.80,12.25],       BER:["Berlin",52.36,13.50],     MAD:["Madrid",40.47,-3.56],
  KEF:["Reykjavík",63.99,-22.62], SVO:["Moskova",55.97,37.41],    CMN:["Kazablanka",33.37,-7.59],
  CAI:["Kahire",30.11,31.41],     NBO:["Nairobi",-1.32,36.93],    CPT:["Cape Town",-33.97,18.60],
  DXB:["Dubai",25.25,55.36],      DOH:["Doha",25.27,51.61],       DEL:["Delhi",28.56,77.10],
  BKK:["Bangkok",13.69,100.75],   SIN:["Singapur",1.36,103.99],   HKG:["Hong Kong",22.31,113.91],
  PEK:["Pekin",40.08,116.58],     ICN:["Seul",37.46,126.44],      HND:["Tokyo",35.55,139.78],
  SYD:["Sidney",-33.94,151.18],   AKL:["Auckland",-37.01,174.79], HNL:["Honolulu",21.32,-157.92],
  LAX:["Los Angeles",33.94,-118.41], JFK:["New York",40.64,-73.78], YYZ:["Toronto",43.68,-79.63],
  MEX:["Meksiko",19.44,-99.07],   GRU:["São Paulo",-23.43,-46.47],EZE:["Buenos Aires",-34.82,-58.54],
  SCL:["Santiago",-33.39,-70.79]
};

/** [kalkış, varış, takma ad] */
export const PRESETS = [
  ["IST","ESB","Kısa iç hat"],
  ["IST","LHR","Avrupa hattı"],
  ["IST","JFK","Atlantik geçişi"],
  ["CDG","HND","Kuzey kutbu üzerinden"],
  ["SCL","AKL","Pasifik boşluğu"],
  ["IST","SYD","Kanguru rotası"]
];

export const cityOf = code => AP[code]?.[0] ?? code;
export const latOf  = code => AP[code][1];
export const lonOf  = code => AP[code][2];

/** Açılır menüler için: şehir adına göre Türkçe sıralı kod listesi. */
export const sortedCodes = () =>
  Object.keys(AP).sort((a, b) => AP[a][0].localeCompare(AP[b][0], "tr"));
