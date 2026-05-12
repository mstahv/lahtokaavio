# Lähtökaavio - Speksit, suunnistuskilpailun lähtöaikojen suunnitteluohjelma

Suunnistuskilpailuissa on monta sarjaa, monta eri rataa ja monta lähtöä. Lähdöt pystyvät käsittelmään vain tietyn määrän yhtäaikaisia lähtijöitä, samalle ykkösrastille ei lähtökohtaisesti saisi lähteä useampia juoksijoita eikä samoille radoille samaan aikaan eri sarjoja. Käytännössä tämä tarkoittaa, että kaikki samasta lähdöstä samalle ykkösrastille lähtevät sarjat sijoitetaan **samaan jonoon**, jolloin niiden lähtöminuutit eivät voi mennä päällekkäin (rinnakkaiset jonot lähtevät samalla minuutilla). Jonossa voi vapaasti olla myös muille ykkösrasteille lähteviä sarjoja. Yleensä lähtöväli on 2 minuuttia sarjan sisällä, mutta joskus myös vain minuutti, joskus 3. Lisäksi joskus kilpailun seurattavuuden takia halutaan räätälöidä miten sarjat lähtevät metsään. Kisa halutaan monesti mahdollisimman kompaktisti, kuitenkaan liikaa kuormittamatta lähtöjä/maalia (hankalampi arvioida). Sarjojen väliin halutaan yleensä 2 varapaikkaa, mahdollisille jälki-ilmoittautuneille ja myöhästyneille.

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

Useampipäiväisessä kilpailussa käyttäjä valitsee kisaa luodessaan, minkä päivän (eli minkä sarakkeen ilmoittautumis-CSV:stä) lähtökaavio suunnitellaan. Kullekin päivälle tehdään oma erillinen lähtökaavionsa. Mahdollinen "Koko tapahtuma" -sarake on koontiluku eikä päivä, joten se ohitetaan parsittaessa.

Käyttäjä hyväksyy tämän jälkeen kisan perusparametrit (ensimmäinen lähtö (oletus 11:00), kuinka monta "tyhjää aikaa" (2) sarjojen väliin, jonojen määrät yms). Parametreissä on myös valintaruutu "salli usean sarjan yhtäaikainen lähtö samalle ykkösrastille, kunhan sarjat eivät lähde samalla minuutilla" — oletuksena pois päältä (jolloin pätee tiukka sääntö: kaikki samalle ykköselle lähtevät samaan jonoon). Kun valinta on päällä, suunnittelija saa jakaa saman ykkösrastin sarjat useammalle jonolle, kunhan jonojen offset-minuutit (esim. parit vs. parittomat lähtöminuutit) ovat eri. Tämä mahdollistaa kompaktimman kaavion silloin kun yksi ykkösrasti vetää poikkeuksellisen monta sarjaa.

Näiden perusteella ohjelma luo ensimmäisen ehdotuksen optimaalisesta lähtöjärjestelystä. Optimoinnin **ensisijainen tavoite on minimoida kilpailun kokonaiskesto** (eli viimeisen lähtevän lähtöaika). Kilpailun pituuden alaraja on luonnostaan suurin yksittäinen ykkösrastikori — sitä ei voi pilkkoa ilman erillistä lupaa (ks. "Allow same first control on parallel lanes" -valinta).

Toissijainen tavoite on **pitää lähdön kuormitus mahdollisimman tasaisena läpi koko kisan**: lyhyitä ykkösrastikoreja yhdistetään samalle jonolle (eri ykkösrastit voivat jakaa jonon vapaasti), niin että jonoja on mahdollisimman vähän mutta ne ovat kaikki suunnilleen yhtä pitkiä. Pakkaus tehdään **Best-Fit-Decreasing** -heuristiikalla: suurin kori menee omalle jonolleen, ja pienemmät kasataan sen mittaisille rinnakkaisille jonoille. Lopullinen virtuaalijonojen joukko jaetaan fyysisille jonopaikoille **offset-minuuttien yli vuorotellen** (esim. parit ja parittomat minuutit), jolloin lähdön kapasiteettia käytetään tasaisesti joka minuutti.

Soft start -vaiheen tarkoitus on **helpottaa lähtötoimitsijoiden työtä kisan alussa**: kaikki jonot eivät käynnisty täydellä teholla minuutilla nolla. Soft start ankkuroituu **per offset-ryhmä**:

- Kunkin offset-ryhmän **pisin jono lähtee heti omasta offset-minuutistaan** (offset 0 → 11:00, offset 1 → 11:01) — näin jokaisella lähtöminuutilla on lähtijä heti ensimmäisestä tikistä asti. Lähtötoimitsijoiden työtahti pysyy heti nominaalisena eikä kisan alkuun synny "kuolleita minuutteja".
- Saman offset-ryhmän **lyhyemmät jonot porrastetaan myöhempään** niiden oman offset-anchorin sisäisen slackin mukaan, enintään **20 minuuttia**.
- "Tyhjiä lähtöminuutteja" saa esiintyä **kisan lopussa** — lyhyemmän offset-ryhmän jonot ehtivät päättyä ennen pitempää offset-ryhmää, ja se on ok (lähtötoimitsijoita helpotetaan etenkin kisan alkuvaiheessa, joka on stressaavin).

Käytännössä: shift_i = min(longestSlotInOffset_i − laneSlot_i, 20 min). Pisimmälle jonolle kussakin offset-ryhmässä shift=0 → starttaa heti offset-minuutilla. Lyhyempi jono samassa offsetissa saa 4–20 min viiveen. Eri offset-ryhmien jonot eivät vaikuta toisiinsa.

Sarjakohtaiset lähtövälit voivat vaihdella samassa lähdössä (esim. eliittisarja 1 min, muut 2 min). Lane-aikaruudukon askel on tällöin lähdössä esiintyvien lähtövälien GCD, ja jokainen sarja askeltaa jonossa eteenpäin omalla välillään (sarjan väli / GCD slottia per lähtijä). Käytännössä tämä pitää kapasiteettirajoituksen (max lähtijää/minuutti) voimassa myös sekoittuneilla väleillä.

Käyttäjä voi raahailla sarjoja eri järjestykseen tai eri jonoihin ja tehdä näin hienosäätöä. Käyttäjän tulee helposti hahmottaa milloin mikäkin jono on valmis ja koska mikäkin sarja alkaa.

Lähtijöiden määriä ja sarjojen väliin varattavia paikkoja tulee myös voida muokata. Sarjakohtaisia lähtijämääriä muokataan omassa näkymässä; muutokset vain pidentävät tai lyhentävät jonoja, lane-rakenne pysyy ennallaan.

Kun ensimmäinen lähtökaavio on luotu, päänäkymä supistuu pieneen työkalupalkkiin: **Add courses** (uusien rata-XML-tiedostojen lisäys), **parametrien lomake** (First start, Start interval, Gap slots, Max starters / minute / start ja "Allow same first control on parallel lanes" -kytkin esitäytettynä nykyisillä arvoilla), sekä **Re-optimize plan** -nappi. Lomakkeen arvot tallennetaan workspaceen myös ilman uudelleenoptimointia, jolloin ne kulkevat mukana JSON-snapshotissa. Re-optimize avaa vahvistusdialogin joka varoittaa että käsin Gantt-näkymässä tehdyt järjestelymuutokset menetetään uudelleenoptimoinnin myötä — käyttäjän pitää erikseen vahvistaa. Yksityiskohtaiset säädöt (juoksijamäärät, järjestely) tehdään omissa näkymissään, joihin pääsee TopLayout-valikon kautta.

Sarjalaatikon taustaväri lasketaan **ykkösrastin koodista** (HSL-hue stable hashista), jolloin samalle ykkösrastille suuntaavat sarjat erottuvat saman värisinä — käyttäjä näkee yhdellä silmäyksellä mitkä sarjat kilpailevat samasta startti-rastista. Tooltipissä näkyy sarjan nimi, lähtijämäärä, lähtöväli, ensimmäinen ja viimeinen lähtöaika sekä ykkösrastin koodi.

Järjestelynäkymä (Gantt-tyylinen) näyttää jonot vaakariveinä ja sarjat aikaan ankkuroituina laatikoina. Käyttäjä voi raahata sarjan toiseen jonoon tai sijoittaa sen täsmälleen kahden olemassa olevan sarjan väliin tai jonon alkuun (drop-piste tunnistetaan kursorin x-sijainnista). Sama operaatio on käytettävissä myös sarjan "…"-valikkoa kautta (peruste: drag&drop ei aina ole käytännöllinen pienillä laitteilla). Manuaalinen siirto ei aja automaattista LPT-optimointia uudestaan; vain lähde- ja kohdejono aikataulutetaan sekvenssin mukaan uudestaan, ja muutos jää voimaan kunnes käyttäjä painaa "Re-optimize plan". Custom-komponentilla on julkinen Tester API (`moveItem(id, lane, index)` / `moveItemToLane`), jonka kautta browserless-testit ajavat käyttäjän operaatiot — DOM- tai valikkorakenteeseen ei testitasolla puututa (ks. `docs/testing-custom-components.md`).

Komponentti `VisualLanePlanner` on irrotettu omaksi domain-riippumattomaksi `visual-lane-planner`-add-on-moduuliksi (perustuu `in.virit:viritin-vaadin-addon-archetype` -arkkityyppiin). Sen API käsittelee `LaneItem`- ja `LanePlan`-arvoja ilman orienteerus-tietoa; `GanttView` muuntaa `SeriesStart ↔ LaneItem` näkymärajalla. Add-on-moduuli sisältää kolme testikerrosta: pure JUnit (`mvn test`), browserless-render (`mvn test`) ja Playwright-IT (`mvn verify`, oikealla Chromiumilla).

Lähtökaavion voi ladata ohjelmasta **JSON-muodossa** (Jackson 3 / `tools.jackson.databind`, `WorkspaceJsonWriter`-luokka, snapshot kattaa kurssit, juoksijamäärät, valitun päivän, parametrit ja generoidun planin). Sama JSON voidaan ladata takaisin ohjelman aloitusnäytön kolmannen Upload-kentän kautta: tila hydratoidaan workspaceen, ja jos planiä oli, käyttäjä viedään suoraan PlanView:hen. JSON on siis ohjelman ensisijainen säilytysmuoto.

Lisäksi tulee olla mahdollisuus viedä **CSV-tiedosto** jatkokäyttöä varten (esim. tulospalveluohjelmaan), riveillä sarjan nimi, ensimmäinen lähtöaika ja viimeinen lähtöaika, "jonon id" (lähdön nimestä johdettu, juokseva numerointi) ja viimeisessä sarakkeessa radan rastikoodit väliviivalla eroteltuina. CSV on vain export — sitä ei tarvitse pystyä lataamaan takaisin ohjelmaan.

Ohjelman tulee varoittaa käyttäjää jos jollekin sarjalle löydy ratatiedostosta radan määrittelyä. Ratatiedostossa voi olla ylimääräisiä ratoja.

## Pari sanaa arkkitehturista:

* Java 25, Maven build
* Spring Boot
* Vaadin 25.2.0-alpha6 (tarvitaan uusin koska samalla testataan browserless-test pakettia ja sen uusimpia ominaisuuksia). Nämä löytyy 
* https://github.com/mstahv/iofdomain kirjasto (IOF xml tiedostojen lukuu)
* Käytetään hyvää java ohjelmointitapaa, perus konventiot, luodaan reilusti ennemin luokkia kuin päättömiä helpperimetodeja. Tästä varmaan ohjeet onkin jo asetuksissa.
* Käytetään Vaadinin oletusteemaa (Aura) sellaisenaan, ei luoda omaa teemaa. UI-tarkennukset tehdään komponenttien tasolla Style-API:lla.
* Räätäläöity Drag and drop komponentti jonojen visualisointiin ja järjestelyyn. Semmoinen hieman GANTT chart tyyppinen
* Erillinen näyttö jossa voi muokata sarjojen kilpailijamääriä ja kilpailun muita parametrejä
* Ohjelmasta tulee hyvinkin UI painotteinen, joten tehdään perus UI/IT testejä kohtuudella. Kuitenkin esim. esimerkkitiedostojen parsinnat ja ehdotetun lähtöjärjestelyn generointi myös utility tasolla.
* UI testit luodaan Vaadin:nin uutta browserless-test hyväksi käyttäen. Huomaa reilusti muuttunut API ja päivitetty dokumentaatio oheisesta PR:stä:https://github.com/vaadin/docs/pull/5581 Aloitetaan triviaalilla läpiviennillä jossa ladataan tiedostot, luodaan automaattisesti lähtökaavio ja ladataan se CSV muodossa. Käytetään browserless-test kirjaston uusia "konteksteja" eikä peritä yläluokkaa. Testauksen edetessä luomme agressiivisesti uusia testejä eri käyttötapauksiin, joten jäsennellän testauskoodia heti alusta lähtien loogisiin helpperehin.
* Ohjelmoinnin ja UI:n kieli englanti, mutta luodaan myös suomenkielinen käännöstiedosto käyttöliittymälle.

## Liitteitä

Hakemistossa iof xml muotoisia ratatiedostoja ja ilmoittautumiset.csv josssa sarjat ja niiden määrät. 
