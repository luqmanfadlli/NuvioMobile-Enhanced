package com.nuvio.app.features.livetv

/**
 * The curated channel line-up behind the Guide's categories: 250 mainstream Canadian and American channels,
 * each with the names providers commonly use for it. One line per channel:
 * `category|country|Display name|other names`, where country is C (Canada) or U (United States) and a
 * trailing `+` means a feed from the other country is acceptable too (a news channel carried in both).
 * Names earlier in a line are preferred over later ones.
 */
internal const val LIVE_TV_GUIDE_CATALOG_TEXT = """
ca_broadcast|C|CBC Television|CBC;CBC Toronto;CBC Montreal;CBC Ottawa;CBC Vancouver;CBC Calgary
ca_broadcast|C|CTV|CTV Toronto;CTV Montreal;CTV Ottawa;CTV Vancouver;CTV Calgary
ca_broadcast|C|CTV2|CTV 2;CTV Two;CTV 2 Toronto;CTV2 Barrie;CTV 2 Barrie;CTV 2 Ottawa;CTV 2 Vancouver
ca_broadcast|C|Global|Global Toronto;Global Montreal;Global Vancouver;Global BC;Global Calgary;Global Edmonton;Global Halifax
ca_broadcast|C|Citytv|City;City TV;City Toronto;City TV Toronto;City Montreal;City Vancouver;City TV Calgary
ca_broadcast|C|CHCH|CHCH Hamilton
ca_broadcast|C|OMNI|OMNI 1;OMNI 2;OMNI Television
ca_broadcast|C|APTN|APTN East
ca_broadcast|C|ICI Radio-Canada Télé|ICI Tele;ICI Tele Montreal;ICI Montreal;ICI Radio Canada;Radio Canada;ICI Tele Quebec;ICI Tele Ottawa
ca_broadcast|C|TVA|TVA Montreal;TVA Quebec
ca_broadcast|C|Noovo|Noovo Montreal;Noovo CFJP
ca_broadcast|C|Télé-Québec|Tele Quebec
ca_broadcast|C|TVO|TVOntario;TV Ontario
ca_broadcast|C|TFO
ca_broadcast|C|Knowledge Network|Knowledge
ca_broadcast|C|AMI-tv|AMI TV;AMI
ca_broadcast|C|AMI-télé|AMI Tele
ca_broadcast|C|APTN Languages|APTN Languages HD
ca_broadcast|C|NTV Newfoundland|NTV
ca_broadcast|C|CHEK|CHEK TV;CHEK Victoria
ca_broadcast|C|YES TV
ca_broadcast|C|TV5|TV 5;TV5 Quebec;TV 5 Quebec;TV5 Quebec Canada
us_broadcast|U|ABC|ABC New York;ABC 7 New York;ABC East
us_broadcast|U|CBS|CBS New York;CBS 2 New York;CBS East
us_broadcast|U|NBC|NBC New York;NBC 4 New York;NBC East
us_broadcast|U|FOX|FOX New York;FOX 5 New York;FOX East
us_broadcast|U|PBS|PBS 13 New York;PBS New York;PBS East;PBS WNET
us_broadcast|U|The CW|CW;CW New York;PIX11;CW 11 New York
us_broadcast|U|MyNetworkTV|My Network TV;My 9;MY9
us_broadcast|U|ION Television|ION;ION TV
us_broadcast|U|Univision|Univision East
us_broadcast|U|Telemundo|Telemundo East
ca_sports|C|TSN 1|TSN1
ca_sports|C|TSN 2|TSN2
ca_sports|C|TSN 3|TSN3
ca_sports|C|TSN 4|TSN4
ca_sports|C|TSN 5|TSN5
ca_sports|C|Sportsnet East|SN East
ca_sports|C|Sportsnet Ontario|SN Ontario
ca_sports|C|Sportsnet West|SN West
ca_sports|C|Sportsnet Pacific|SN Pacific
ca_sports|C|Sportsnet One|SN One;SN1;Sportsnet 1
ca_sports|C|Sportsnet 360|SN 360;SN360
ca_sports|C|RDS|RDS 1
ca_sports|C|RDS2|RDS 2
ca_sports|C|TVA Sports|TVA Sports 1
ca_sports|C|TVA Sports 2
ca_sports|C|NBA TV Canada|NBA TV
ca_sports|C|RDS Info
ca_sports|C|Fight Network|The Fight Network
ca_sports|C|Game+|Game Plus;GameTV;Game TV
ca_sports|C|Sportsnet World|SN World;Sportsnet World Extra 1
ca_sports|C|Fox Sports Racing
ca_sports|C|REV TV Canada|REV TV
us_sports|U|ESPN
us_sports|U|ESPN2|ESPN 2
us_sports|U|FS1|Fox Sports 1;FS 1
us_sports|U|FS2|Fox Sports 2;FS 2
us_sports|U|CBS Sports Network|CBS Sports;CBSSN
us_sports|U|NFL Network
us_sports|U|NFL RedZone|NFL Red Zone;RedZone
us_sports|U|MLB Network
us_sports|U|NHL Network
us_sports|U|NBA TV
us_sports|U|Golf Channel|Golf
us_sports|U|Tennis Channel
us_sports|U|ESPNU|ESPN U
us_sports|U|ESPNews|ESPN News
us_sports|U|ACC Network|ESPN ACC Network
us_sports|U|SEC Network|ESPN SEC Network
us_sports|U|Big Ten Network|BTN;Big 10 Network;B1G Network
us_sports|U|NBCSN|NBC Sports Network;NBC Sports
us_sports|U|TUDN|Univision TUDN
us_sports|U|ESPN Deportes
us_sports|U|FOX Deportes
us_sports|U|beIN Sports|beIN Sports USA;beIN Sports 1
us_sports|U|beIN Sports en Español|beIN Sports Espanol;beIN Sports En Espanol;beIN Sports Spanish
us_sports|U|Willow TV|Willow;Willow Cricket;Willow HD
us_sports|U|FanDuel TV|TVG;FanDuel TV TVG
news|C|CBC News Network|CBC News;CBC NN;CBC Newsworld
news|C|CTV News Channel|CTV News;CTV Newsnet
news|C|CP24
news|C|BNN Bloomberg|BNN
news|C|ICI RDI|RDI
news|C|LCN|Le Canal Nouvelles
news|C|The Weather Network|Weather Network
news|C|MétéoMédia|Meteo Media;Meteomedia;Meteo Media Montreal
news|C|CPAC English|CPAC
news|C|CPAC Français|CPAC French;CPAC Francais
news|C|Global News BC 1|Global News BC1;BC 1;BC1;Global National News
news|U+|CNN|CNN US;CNN USA
news|U+|Fox News|Fox News Channel
news|U+|MS NOW|MSNBC
news|U+|CNBC
news|U+|NewsNation|News Nation
news|U+|Fox Business|Fox Business Network
news|U+|Bloomberg Television|Bloomberg;Bloomberg TV
news|U+|BBC News|BBC World News
news|U+|CNN International
news|U+|C-SPAN2|C-SPAN 2;CSPAN 2;CSPAN2
news|U+|C-SPAN3|C-SPAN 3;CSPAN 3;CSPAN3
news|U+|Newsmax|Newsmax TV
news|U|The Weather Channel|Weather Channel
news|U|FOX Weather
news|U+|ABC News Live|ABC News
us_ent|U|USA Network|USA
us_ent|U|TNT
us_ent|U|TBS
us_ent|U|FX
us_ent|U|FXX
us_ent|U|AMC
us_ent|U|Bravo
us_ent|U|A&E|A and E;AE;A&E Network
us_ent|U|Comedy Central
us_ent|U|Paramount Network|Paramount
us_ent|U|HGTV
us_ent|U|Food Network
us_ent|U|Discovery Channel|Discovery
us_ent|U|History|History Channel;The History Channel
us_ent|U|TLC
us_ent|U|Hallmark Channel|Hallmark
us_ent|U|Hallmark Mystery|Hallmark Movies & Mysteries;Hallmark Movies and Mysteries;Hallmark Movies Mysteries
us_ent|U|Hallmark Family|Hallmark Drama
us_ent|U|Lifetime
us_ent|U|Lifetime Movie Network|LMN;Lifetime Movies;Lifetime Movie
us_ent|U|WE tv|WE
us_ent|U|TV Land|TVLand
us_ent|U|E!|E;E Entertainment;E! Entertainment
us_ent|U|BET
us_ent|U|BET Her
us_ent|U|VH1|VH 1
us_ent|U|MTV
us_ent|U|CMT
us_ent|U|OWN|Oprah Winfrey Network
us_ent|U|Game Show Network|GSN
us_ent|U|SYFY|Sy Fy
us_ent|U|IFC
us_ent|U|SundanceTV|Sundance TV;Sundance
ca_specialty|C|Showcase|Showcase East
ca_specialty|C|W Network|W
ca_specialty|C|Slice
ca_specialty|C|CTV Drama|CTV Drama Channel
ca_specialty|C|CTV Comedy|CTV Comedy Channel;Comedy Network
ca_specialty|C|CTV Sci-Fi|CTV Sci-Fi Channel;CTV SciFi;CTV Scifi Channel;Space
ca_specialty|C|Discovery Canada|Discovery Channel;Discovery
ca_specialty|C|HGTV Canada|HGTV
ca_specialty|C|Food Network Canada|Food Network
ca_specialty|C|History Canada|History;History Channel
ca_specialty|C|CTV Life|CTV Life Channel
ca_specialty|C|CTV Nature|CTV Nature Channel
ca_specialty|C|CTV Wild|CTV Wild Channel;Animal Planet
ca_specialty|C|CTV Speed|CTV Speed Channel
ca_specialty|C|Bravo Canada|Bravo
ca_specialty|C|USA Network Canada|USA Network
ca_specialty|C|E! Canada|E;E!;E Entertainment
ca_specialty|C|Much|MuchMusic;Much Music
ca_specialty|C|Adult Swim Canada|Adult Swim
ca_specialty|C|FX Canada|FX
ca_specialty|C|FXX Canada|FXX
ca_specialty|C|Home Network
ca_specialty|C|Flavour Network
ca_specialty|C|Magnolia Network Canada|Magnolia Network;Magnolia
ca_specialty|C|Investigation Discovery Canada|Investigation Discovery;Discovery ID;ID
ca_specialty|C|Cottage Life
ca_specialty|C|Love Nature
ca_specialty|C|BBC Earth Canada|BBC Earth
french|C|Unis TV|Unis
french|C|ICI ARTV|ARTV
french|C|ICI Explora|Explora
french|C|Séries+|Series Plus
french|C|ADDIK|Addik TV
french|C|Z|Z Tele;Ztele
french|C|Canal D
french|C|Canal Vie|Vie
french|C|Historia
french|C|Évasion|Evasion
french|C|Zeste
french|C|CASA
french|C|Prise 2
french|C|Cinépop|Cinepop
docs|U|Animal Planet
docs|U|National Geographic|Nat Geo;NatGeo
docs|U|Nat Geo Wild|National Geographic Wild;NatGeo Wild
docs|U|Investigation Discovery|ID;Discovery Investigation;ID Investigation Discovery
docs|U|Science Channel|Discovery Science;Science
docs|U|Travel Channel|Travel
docs|U|Cooking Channel
docs|U|Magnolia Network|Magnolia
docs|U|Discovery Life
docs|U|Smithsonian Channel|Smithsonian
docs|U|American Heroes Channel|AHC
docs|U|Oxygen|Oxygen True Crime
kids|C|YTV
kids|C|Treehouse|Treehouse TV
kids|C|Cartoon Network Canada|Cartoon Network
kids|C|Boomerang Canada|Boomerang
kids|C|Disney Channel Canada|Disney Channel;Disney
kids|C|Télétoon Français|Teletoon;Teletoon Francais;Tele Toon
kids|U|Disney Channel|Disney
kids|U|Nickelodeon|Nick
kids|U|Cartoon Network
kids|U|Nick Jr.|Nick Jr;Nick Junior
kids|U|Nicktoons
kids|U|TeenNick|Teen Nick
kids|U|Disney Junior|Disney Jr
kids|U|Disney XD
kids|U|Boomerang
kids|U|Freeform
kids|U|PBS Kids
premium|U|HBO
premium|U|Paramount+ with SHOWTIME|Showtime;Paramount+ with Showtime;Paramount Plus with Showtime
premium|U|STARZ
premium|C|Crave 1|Crave;TMN1E Crave 1 TV;TMN1E Crave TV 1;Crave 1 TV;Crave TV 1
premium|C|Super Écran 1|Super Ecran;Super Ecran 1
premium|C|Crave 2|TMN2E Crave 2 TV;TMN2E Crave TV 2;Crave 2 TV;Crave TV 2
premium|C|Crave 3|TMN3E Crave 3 TV;TMN3E Crave TV 3;Crave 3 TV;Crave TV 3
premium|C|Crave 4|TMN4E Crave 4 TV;TMN4E Crave TV 4;Crave 4 TV;Crave TV 4
premium|C|HBO 1 Canada|HBO 1;HBO Canada 1;HBO Canada
premium|C|HBO 2 Canada|HBO 2;HBO Canada 2
premium|C|STARZ 1 Canada|STARZ 1;Starz Canada 1
premium|C|STARZ 2 Canada|STARZ 2;Starz Canada 2
premium|C|Super Écran 2|Super Ecran 2
premium|C|Super Écran 3|Super Ecran 3
premium|C|Super Écran 4|Super Ecran 4
premium|C+|Hollywood Suite 70s+|Hollywood Suite 70s;Hollywood Suite 70s Movies
premium|C+|Hollywood Suite 80s & 90s|Hollywood Suite 80s;Hollywood Suite 80s Movies;Hollywood Suite 90s;Hollywood Suite 90s Movies
premium|C+|Hollywood Suite 2000s|Hollywood Suite 00s;Hollywood Suite 00s Movies;Hollywood Suite 2000s Movies
premium|C+|Hollywood Suite 2010s+|Hollywood Suite 2010s;Hollywood Suite 10s
premium|U|HBO2|HBO 2
premium|U|HBO Signature
premium|U|HBO Family
premium|U|HBO Comedy
premium|U|HBO Zone
premium|U|HBO Latino
premium|U|Cinemax
premium|U|MoreMax|Cinemax MoreMax
premium|U|ActionMax|Cinemax ActionMax
premium|U|ThrillerMax|Cinemax ThrillerMax
premium|U|Showtime 2
premium|U|Showtime Extreme
premium|U|Showtime Showcase
premium|U|The Movie Channel|TMC;Movie Channel
premium|U|FLIX
premium|U|STARZ Edge
premium|U|STARZ Cinema
premium|U|STARZ Comedy
premium|U|MGM+|MGM Plus;MGM;Epix
premium|U|Turner Classic Movies|TCM
premium|U|STARZ Encore|Encore
premium|U|MGM+ Hits|MGM Plus Hits;Epix Hits
"""
