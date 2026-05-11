# Lähtökaavio - Speksit, suunnistuskilpailun lähtöaikojen suunnitteluohjelma

Suunnistuskilpailuissa on monta sarjaa, monta eri rataa ja monta lähtöä. Lähdöt pystyvät käsittelmään vain tietyn määrän yhtäaikaisia lähtijöitä, samalle ykkösrastille ei lähtökohtaisesti saisi lähteä useampia juoksijoita eikä samoille radoille samaan aikaan eri sarjoja. Yleensä lähtöväli on 2 minuuttia sarjan sisällä, mutta joskus myös vain minuutti, joskus 3. Lisäksi joskus kilpailun seurattavuuden takia halutaan räätälöidä miten sarjat lähtevät metsään. Kisa halutaan monesti mahdollisimman kompaktisti, kuitenkaan liikaa kuormittamatta lähtöjä/maalia (hankalampi arvioida). Sarjojen väliin halutaan yleensä 2 varapaikkaa, mahdollisille jälki-ilmoittautuneille ja myöhästyneille.

Käytännössä suunnittelua tehdään nykyisin manuaalisesti miettimällä "lähtöjonoja", joihin sarjoja tiputellaan. Niitä voi sitten olla n kpl per lähtö, riippuen lähdön koosta ja lähtövälistä (jonot voivat olla "limittäin"). Hyvä oletus on että maksimissaan 6 lähtee samalla minuuutilla samasta lähtöstä, mutta tätä parametria tulee voida säätää manuaalisesti.

Jonojen määrä per lähtö johdetaan käyttäjän asettamasta maksimista (lähtijää/minuutti/lähtö) ja kyseisessä lähdössä käytössä olevien sarjojen lähtöväleistä. Lähtöväli voi vaihdella sarjakohtaisesti (1, 2 tai 3 minuuttia), joten esim. jos maksimi on 6/min ja sarjan lähtöväli on 2 min, kyseinen sarja tarvitsee 12 limittäistä jonopaikkaa täyttääkseen kapasiteetin — käytännössä jonojen tarvittava määrä lasketaan dynaamisesti sen mukaan, mitä sarjoja samaan lähtöön on sijoitettu.

Ohjelmaa käynnistäessä käyttäjä lataa siihen kaksi tiedostoa, toisessa on radat (IOF XML, tuki sekä versiolle 2 & 3, näitä voi olla useampia joska monesti 2-3 ratamestariea) ja toisessa sarjoihin ilmoittautuneiden määrä CSV tiedostossa. Useampiosaisessa kilpailussa sarakkeissa eri määrät. Esim. :

```
Sarja;Päivä #1;Päivä #2;Päivä #3;Päivä #4;Päivä #5;Koko tapahtuma
D10RR;10;10;10;10;10;10
D12;20;20;20;20;20;20
D12TR;9;10;9;10;9;10
D13;9;9;9;9;9;9
````

IOF XML tiedostossa on ratatiedot, mukaanlukien lähtöpaikat. Jos sama lähtöpaikka esiintyy useammalla radalla, kyseessä on sama fyysinen lähtö ja sarjat jakavat saman kapasiteetin.

Useampipäiväisessä kilpailussa käyttäjä valitsee kisaa luodessaan, minkä päivän (eli minkä sarakkeen ilmoittautumis-CSV:stä) lähtökaavio suunnitellaan. Kullekin päivälle tehdään oma erillinen lähtökaavionsa.

Käyttäjä hyväksyy tämän jälkeen kisan perusparametrit (ensimmäinen lähtö (oletus 11:00), kuinka monta "tyhjää aikaa" (2) sarjojen väliin, jonojen määrät yms)

Näiden perusteella ohjelma luo ensimmäisen ehdotuksen optimaalisesta lähtöjärjestelystä. Optimoinnin **ensisijainen tavoite on minimoida kilpailun kokonaiskesto** (eli viimeisen lähtevän lähtöaika): käytännössä tämä toteutuu pitämällä jonot mahdollisimman saman mittaisina, jotta mikään yksittäinen jono ei venytä kokonaisuutta. Toissijaisesti lähtöjä käynnistetään mieluummin "hitaasti" kuin "sata lasissa" — jos jokin jono jää lyhyemmäksi kuin muut, se kannattaa ennemmin käynnistää hieman myöhemmin, jotta ensimmäisillä minuuteilla lähtössä on hieman vähemmän lähtijöitä. Käyttäjä voi sitten raahailla sarjoja eri järjestykseen tai eri jonoihin ja tehdä näin hienosäätöä. Käyttäjän tulee helposti hahmottaa milloin mikäkin jono on valmis ja koska mikäkin sarja alkaa.

Lähtijöiden määriä ja sarjojen väliin varattavia paikkoja tulee myös voida muokata. 

Lähtökaavion voi ladata ohjelmasta **JSON-muodossa** (generoidaan DTO:ista Jacksonilla) ja sama JSON voidaan myöhemmin ladata takaisin ohjelmaan mahdollisia lisäsäätöjä varten — tämä on vaihtoehtoinen tapa aloittaa käyttö. JSON on siis ohjelman ensisijainen säilytysmuoto.

Lisäksi tulee olla mahdollisuus viedä **CSV-tiedosto** jatkokäyttöä varten (esim. tulospalveluohjelmaan), riveillä sarjan nimi, ensimmäinen lähtöaika ja viimeinen lähtöaika, "jonon id" (lähdön nimestä johdettu, juokseva numerointi) ja viimeisessä sarakkeessa radan rastikoodit väliviivalla eroteltuina. CSV on vain export — sitä ei tarvitse pystyä lataamaan takaisin ohjelmaan.

Ohjelman tulee varoittaa käyttäjää jos jollekin sarjalle löydy ratatiedostosta radan määrittelyä. Ratatiedostossa voi olla ylimääräisiä ratoja.

## Pari sanaa arkkitehturista:

* Java 25, Maven build
* Spring Boot
* Vaadin 25.2.0-alpha6 (tarvitaan uusin koska samalla testataan browserless-test pakettia ja sen uusimpia ominaisuuksia). Nämä löytyy 
* https://github.com/mstahv/iofdomain kirjasto (IOF xml tiedostojen lukuu)
* Käytetään hyvää java ohjelmointitapaa, perus konventiot, luodaan reilusti ennemin luokkia kuin päättömiä helpperimetodeja. Tästä varmaan ohjeet onkin jo asetuksissa.
* Räätäläöity Drag and drop komponentti jonojen visualisointiin ja järjestelyyn. Semmoinen hieman GANTT chart tyyppinen
* Erillinen näyttö jossa voi muokata sarjojen kilpailijamääriä ja kilpailun muita parametrejä
* Ohjelmasta tulee hyvinkin UI painotteinen, joten tehdään perus UI/IT testejä kohtuudella. Kuitenkin esim. esimerkkitiedostojen parsinnat ja ehdotetun lähtöjärjestelyn generointi myös utility tasolla.
* UI testit luodaan Vaadin:nin uutta browserless-test hyväksi käyttäen. Huomaa reilusti muuttunut API ja päivitetty dokumentaatio oheisesta PR:stä:https://github.com/vaadin/docs/pull/5581 Aloitetaan triviaalilla läpiviennillä jossa ladataan tiedostot, luodaan automaattisesti lähtökaavio ja ladataan se CSV muodossa. Käytetään browserless-test kirjaston uusia "konteksteja" eikä peritä yläluokkaa. Testauksen edetessä luomme agressiivisesti uusia testejä eri käyttötapauksiin, joten jäsennellän testauskoodia heti alusta lähtien loogisiin helpperehin.
* Ohjelmoinnin ja UI:n kieli englanti, mutta luodaan myös suomenkielinen käännöstiedosto käyttöliittymälle.

## Liitteitä

Hakemistossa iof xml muotoisia ratatiedostoja ja ilmoittautumiset.csv josssa sarjat ja niiden määrät. 
