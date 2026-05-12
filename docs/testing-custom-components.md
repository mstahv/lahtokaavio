# Testing custom Vaadin components in browserless tests

Opit `GanttComponent`-toteutuksesta (drag & drop + "..." valikko). Tämä
dokumentti kuvaa, miksi me **emme** ohjaa testejä DOMin läpi, ja minkä-
laisen tester-API:n custom-komponentille kannattaa rakentaa.

## Lähtökohta

Browserless-test ajaa Vaadinia ilman selainta — eli ilman oikeaa DOMia,
puuttumatta käyttäjän mouse/keyboard-tapahtumiin. Useimmille standardi-
komponenteille `TesterWrappers` tarjoaa simulaatio-API:n (esim.
`UploadTester.upload(bytes)`, `ButtonTester.click()`, `ComboBoxTester
.selectItem(label)`). Custom-komponentilla niitä ei ole.

Vaihtoehdot ovat:

1. **DOM-tason simulaatio** (ei suositella): kaivaa esiin sisäiset
   elementit `getElement().getChildren()`-puusta ja kutsu
   `fireEvent(...)`. Hauras: vaihtaa rikki heti kun layout muuttuu.
2. **Public tester API komponentilla** (suositeltava): komponentti
   tarjoaa itse julkisen rajapinnan, joka simuloi käyttäjän operaatiot
   mallitasolla. Drag&drop, valikkokomennot ja näppäimet ohjataan
   saman metodin kautta — tarkalleen samoja koodipolkuja, joita
   sisäiset event-handlerit kutsuvat.

## Suunnittelusääntö

Erottele kolmeen kerrokseen:

```
[ DOM / event listeners ] ──┐
                            ▼
                  ┌─ public mutating API ──┐
[ "..." valikon item.click ]               │
[ drop-listenerin onDrop ]   ─►  moveSeriesToLane(name, lane)
[ test().moveSeriesToLane(...) ]           │
                            ▼
                     [ model.moveToLane → re-time ]
                            │
                            ▼
                  [ render() + onChange callback ]
```

`moveSeriesToLane` on **yksi totuus**. Valikko ja drop-listener vain
kutsuvat sitä. Testi kutsuu sitä **suoraan** komponentin julkisen
viittauksen kautta. Näin testi ei väitä mitään valikon nimestä tai
DOM-rakenteesta — vain käyttäytymisestä.

## Esimerkki — `GanttComponent`

```java
public class GanttComponent extends Composite<VerticalLayout> {

    // --- Tester API ---
    public List<String> laneIds() { ... }
    public String laneOf(String seriesName) { ... }
    public LocalTime firstStartOf(String seriesName) { ... }
    public void moveSeriesToLane(String seriesName, String targetLaneId) {
        model.moveToLane(seriesName, targetLaneId);
        render();
        onChange.accept(model.toStartPlan());
    }
}
```

Käyttäjälle näkyvät polut:

```java
// "..." valikon item
MenuItem item = dots.getSubMenu().addItem("Move to " + laneId,
        e -> moveSeriesToLane(s.seriesName(), laneId));

// Drop-listener
dropTarget.addDropListener(event -> event.getDragData().ifPresent(data -> {
    if (data instanceof String seriesName) {
        moveSeriesToLane(seriesName, laneId);
    }
}));
```

Testi:

```java
GanttView view = ext.navigate(GanttView.class);
GanttComponent gantt = view.ganttComponent();
gantt.moveSeriesToLane("H21E", "STA1-3");
assertThat(gantt.laneOf("H21E")).isEqualTo("STA1-3");
```

## Miksi tämä tapa

- **Korruptioresistenssi**: DOM-rakenne (esim. `MenuBar` vai
  `ContextMenu`, "..." vai kuvake) vaihtuu helposti. Mallin tason
  metodi on stabiili sopimus.
- **Testin selkeys**: testin lukijan ei tarvitse arvata miten valikko
  on rakennettu — kutsu kertoo intentin.
- **Performanssi**: ei renderöintien välistä polling-/wait-logiikkaa.
- **Tuplakatto**: jos lisätään uusi syöttötapa (esim. pikanäppäin),
  se kutsuu samaa metodia ja sama testi kattaa edelleen koko polun.

## Mikä ei korvaa selain-testiä

Tester-API ei tarkista, näkyykö "..." -valikko **käyttäjälle**
(visibility, z-index, css-clippaus). Sitä varten tarvitaan joko:

- Playwright-testi tuotantokokoonpanossa, tai
- Snapshot-testi staattiselle DOM-rakenteelle.

Mutta browserless-testin oikea taso on toiminnallisuus, ei piksellien
sijainti.

## Yhteenveto

1. **Älä** tee DOM-kaivelua testissä custom-komponentin sisältä.
2. **Tarjoa** komponentilla julkinen rajapinta käyttäjän intentin
   tasolla.
3. **Ohjaa** kaikki syöttötavat (valikko, drag&drop, näppäimet, testi)
   saman metodin läpi.
4. **Dokumentoi** API:n metodit komponentin javadocissa "Tester API"
   -otsikon alla — selventää sekä lukijalle että tuleville testeille.

## Multi-module + Playwright

Kun komponentti irrotettiin omaksi `visual-lane-planner`-moduuliksi,
testaus jakautui kolmeen tasoon:

| Taso                | Sijainti                              | Käynnistys          |
| ------------------- | -------------------------------------- | ------------------- |
| Pure JUnit          | `unit/VisualLanePlannerTest.java`     | `mvn test`          |
| Browserless render  | `unit/*BrowserlessTest.java`          | `mvn test`          |
| Playwright (oikea selain) | `it/*IT.java` (failsafe-plugin) | `mvn verify`        |

- **Pure JUnit** on nopein ja toimii ilman Vaadin-UI:n bootia. Testaa
  vain mallin tason käyttäytymistä (`moveItemToLane`,
  `firstStartOf`...).
- **Browserless** ajaa todellisen Vaadin-renderöinnin mock-UIssa.
  Komponentit lisätään DOMiin, voimme tehdä `$(Div.class).withAttribute("data-item", "alpha")` ja vastaavia kyselyitä. Drag&drop ja
  menu kutsuvat siltikin sisäisesti samaa `moveItemToLane`-metodia;
  testit ajavat sitä suoraan, eivät simuloi event-tasolla.
- **Playwright** ajaa oikean Chromiumin Spring Boot -palvelimen
  päällä, navigoi route-URL:iin, odottaa että `[data-item]` -elementit
  näkyvät, ja varmistaa ettei tule JS-virheitä (`mopo.assertNoJsErrors()`).
  Ei myöskään simuloi varsinaista raahausta — sen oikea testaus
  vaatii integraatiokerroksen jonka kompleksisuus ei tuota
  vastaavaa hyötyä.

Suositus uusille custom-komponenteille: ota mukaan kaikki kolme.
Pure JUnit kertoo että logiikka on kunnossa, browserless että render
toimii, Playwright että selain on tyytyväinen.
