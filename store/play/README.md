# Store-Assets

Grafiken für einen Eintrag im Google Play Store. Sie werden **nicht** ins APK
gepackt und liegen deshalb außerhalb des Android-Moduls.

## App-Symbol

| Datei | Variante |
|---|---|
| `icon-512.png` | Android-Grün, entspricht dem Standard-Startsymbol |
| `icon-512-blau.png` | WordPress-Blau – weisse Blase auf blauem Grund, siehe unten |

Erzeugt aus derselben Zeichnung wie das Launcher-Symbol
(`android/app/src/main/res/drawable/ic_launcher_foreground.xml`).

### Einhaltung der Spezifikation

Google Play stellt an das Symbol andere Anforderungen als Android an das
Startsymbol. Geprüft wurde gegen
[Spezifikationen für das App-Symbol](https://developer.android.com/distribute/google-play/resources/icon-design-specifications):

| Vorgabe | Umsetzung |
|---|---|
| 512 × 512 px | ✅ |
| 32-Bit-PNG | ✅ RGBA, 8 Bit je Kanal |
| Farbraum sRGB | ✅ |
| höchstens 1024 KB | ✅ 11,7 KB |
| volles Quadrat, keine runden Ecken | ✅ Play legt 30 % Eckenradius selbst an |
| kein Schlagschatten | ✅ Play erzeugt ihn selbst |
| Hintergrundfarbe statt Transparenz | ✅ durchgehend `#10141B` |
| kein Text, keine Auszeichnungen, keine Rangangaben | ✅ |

Die Marke misst 328 × 304 px, also 64 % der Kantenlänge, und liegt damit
deutlich innerhalb des Eckenradius von 154 px, den Play anlegt.

Sie sitzt **optisch** zentriert, nicht geometrisch: Der Blasenkörper trägt
nahezu die gesamte Fläche, der Schweif wiegt kaum etwas. Am Rahmen
ausgerichtet wirkte die Marke deshalb nach oben gerutscht.

## Warum die blaue Variante umgekehrt eingefärbt ist

Blau `#3858E9` auf dem dunklen Grund erreicht nur einen Kontrast von 3,3:1;
der Schweif der Sprechblase verschwindet dabei bei kleinen Größen fast. Mit
weisser Blase auf blauem Grund sind es 5,6:1 in beide Richtungen, bei
unveränderter Markenfarbe.

## Warum größer als im Startsymbol

Im Startsymbol begrenzt die Maske des Herstellers die nutzbare Fläche auf
66 von 108 dp – die Marke füllt dort 60,5 dp. Bei Play beschneidet nur der
Eckenradius, deshalb darf die Zeichnung einen größeren Anteil einnehmen. Die
Proportionen der Zeichnung selbst sind in beiden Fällen dieselbe.

## Neu erzeugen

Die Dateien sind aus `docker/`-unabhängigen SVG-Quellen gerendert. Bei einer
Änderung am Symbol müssen sie neu erzeugt werden; die Pfaddaten stehen in
`ic_launcher_foreground.xml` und lassen sich unverändert in ein SVG
übernehmen.
