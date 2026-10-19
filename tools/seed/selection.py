#!/usr/bin/env python3
"""Selection rules for the vegan starter dataset (curated, reviewable).

Scope (AGENTS/product decision): a vegan base list — fruit, vegetables,
potatoes, cereals/rice, bread, pasta, pulses, nuts, oils, drinks/juices and
basic seasoning. Meat, dairy, fish, cheese and eggs are excluded by name.

Two mechanisms:

* ``HANDPICKED`` — explicit BLS codes per category. Used wherever keyword
  matching is unreliable (bread, cereals, pasta, juices, pantry items).
* ``CATEGORIES`` — keyword matching with a per-keyword claim budget and a
  ranking that prefers the plain ``roh`` variant. Used for the long tail of
  fruit and vegetables, where the BLS names are predictable.

Every handpicked code must exist in BLS 4.0 and pass the vegan filter —
``build_seed.py`` fails otherwise.
"""

from __future__ import annotations

# label -> BLS codes (claimed before any keyword, never overridden)
HANDPICKED: dict[str, list[str]] = {
    "Obst & Säfte": [
        # Säfte (100 %-Direktsäfte)
        "F110600",  # Apfelsaft
        "F130600",  # Birnensaft
        "F310600",  # Traubensaft
        "F211600",  # Süßkirschensaft
        "F301600",  # Erdbeersaft
        "F304600",  # Heidelbeersaft
        "F501600",  # Ananassaft
        "F514600",  # Kiwisaft
        "F603600",  # Orangensaft
        "F601600",  # Zitronensaft
        "F010600",  # Mehrfruchtsaft
    ],
    "Brot & feines Gebäck": [
        "B101000",  # Vollkornbrot
        "B111000",  # Weizenvollkornbrot
        "B111200",  # Weizenvollkorntoastbrot
        "B121000",  # Roggenvollkornbrot
        "B221000",  # Roggenbrot
        "B271000",  # Roggenmischbrot
        "B311000",  # Weizenbrot/Weißbrot
        "B314100",  # Weizentoastbrot (ohne Butter)
        "B511000",  # Weizenbrötchen
        "B521000",  # Roggenbrötchen
        "B401000",  # Vollkornbrötchen
        "B6A2000",  # Roggenknäckebrot
        "B6A2100",  # Roggenvollkornknäckebrot
        "B6A4000",  # Mehrkornvollkornknäckebrot
        "B780500",  # Pumpernickel
        "B783012",  # Weizentortilla
    ],
    "Getreide, Reis & Pseudogetreide": [
        "C131000",  # Hafer, ganzes Korn, roh
        "C133000",  # Haferflocken
        "C341000",  # Mais, roh
        "C351000",  # Reis unpoliert, roh
        "C352000",  # Reis poliert, roh
        "C353100",  # Wildreis, roh
        "C359000",  # Reis parboiled, poliert, roh
        "C118000",  # Quinoa, roh
        "C119100",  # Bulgur, roh
        "C119200",  # Couscous, roh
        "C322000",  # Buchweizen, roh
        "C332000",  # Hirse, roh
        "C335000",  # Amaranth, roh
        "C211000",  # Weizenvollkornmehl
        "C214100",  # Weizenmehl Type 405
        "C214200",  # Weizenmehl Type 550
        "C221000",  # Roggenvollkornmehl
        "C223100",  # Roggenmehl Type 815
        "C234100",  # Dinkelmehl Type 812
        "C235000",  # Dinkenvollkornmehl
        "C424000",  # Buchweizenmehl
        "C443000",  # Maismehl
        "C219300",  # Hartweizengrieß
        "C512000",  # Müsli/Basismischung, ungesüßt
        "C515400",  # Cornflakes, ungesüßt
        "C533000",  # Popcorn, ungesüßt
        "C532700",  # Reiswaffeln, ungesalzen
        "C559000",  # Reisnudeln, roh
        "C558000",  # Fleischersatz glutenhaltig (Seitan)
    ],
    "Nudeln & Teigwaren": [
        "E401000",  # Teigwaren eifrei, roh
        "E510000",  # Vollkornteigwaren eifrei, roh
        "E612000",  # Teigwaren eifrei, glutenfrei, roh
        "E632000",  # Dinkelteigwaren eifrei, roh
        "E632200",  # Dinkelvollkornteigwaren eifrei, roh
        "E811000",  # Frischteigwaren eifrei, roh
        "E605000",  # Schupfnudeln, roh
    ],
    "Kartoffeln & Pilze": [
        "K120100",  # Kartoffel ungeschält, roh
        "K110132",  # Kartoffel geschält, gekocht
        "K420100",  # Batate/Süßkartoffel, roh
        "K701100",  # Champignon roh
        "K740100",  # Austernpilz roh
        "K718100",  # Steinpilz roh
        "K724100",  # Shiitakepilz roh
        "K750100",  # Mischpilze roh
        "K762400",  # Hefe Flocken/Nährhefe
    ],
    "Hülsenfrüchte, Nüsse & Samen": [
        # frisch/grün (BLS-Gruppe G)
        "G710100",  # Bohne grün, roh
        "G730100",  # Dicke Bohne unreif, roh
        "G750100",  # Sojabohne unreif (Edamame), roh
        "G760100",  # Erbse grün, roh
        # reif/getrocknet (Gruppe H + G)
        "G760400",  # Erbse reif
        "G770400",  # Kichererbse reif
        "G780400",  # Limabohne reif
        "G750400",  # Sojabohne reif
        "H702100",  # Augenbohne reif
        "H739400",  # Gartenbohne reif
        "H742100",  # Kidneybohne reif
        "H750100",  # Dicke Bohne reif
        "H725100",  # Linse reif
        "H730000",  # Linse rot reif
        "H861000",  # Tofu
        "H861100",  # Seidentofu
        "H765000",  # Sojaschnetzel/Sojagranulat
        "H960000",  # Hummus/Kichererbsenmus
        "H154000",  # Kokosmilch/Kokosnussmilch
        "H510802",  # Oliven grün, gesäuert, abgetropft
        "H030400",  # Glasnudeln aus Mungbohnenstärke
        # Nüsse & Samen
        "H110600",  # Erdnuss geröstet
        "H120100",  # Walnuss
        "H130100",  # Haselnuss
        "H160100",  # Pekannuss
        "H170100",  # Cashewkern
        "H180100",  # Paranuss
        "H190100",  # Macadamianuss
        "H210100",  # Mandel süß
        "H230100",  # Edelkastanie/Marone
        "H250100",  # Pistazie
        "H320100",  # Pinienkern
        "H410100",  # Leinsamen
        "H420100",  # Sesam
        "H430100",  # Sonnenblumenkern
        "H450100",  # Mohn
        "H480100",  # Chia-Samen
        "H310100",  # Kürbiskern
        "H810000",  # Studentenfutter
        # Mus & Aufstriche
        "H110800",  # Erdnussmus
        "H130800",  # Haselnussmus
        "H212800",  # Mandelmus
        "H880200",  # Erdnussbutter/Erdnusscreme
        "Q901000",  # Tahin (Sesammus)
    ],
    "Öle & Fette": [
        "Q120000",  # Olivenöl
        "Q180000",  # Rapsöl/Rüböl
        "Q320000",  # Sonnenblumenöl
        "Q270000",  # Sojaöl
        "Q160000",  # Leinöl
        "Q170000",  # Hanföl
        "Q230000",  # Sesamöl
        "Q250000",  # Kürbiskernöl
        "Q310000",  # Walnussöl
        "Q210000",  # Erdnussöl
        "Q550000",  # Kokosöl
        "Q400000",  # Pflanzenmargarine Vollfett
        "Q440000",  # Pflanzenmargarine Halbfett
    ],
    "Getränke": [
        "N110000",  # Trinkwasser
        "N120000",  # Natürliches Mineralwasser
        "N127000",  # Natürliches Mineralwasser mit Kohlensäure
        "N128000",  # Natürliches Mineralwasser still
        "N410100",  # Kaffee (Getränk)
        "N420100",  # Kaffee (Getränk) aus Instantpulver
        "N411100",  # Espresso (Getränk)
        "N415000",  # Kaffee (Getränk) entkoffeiniert
        "N610100",  # Grüntee (Getränk)
        "N630000",  # Schwarztee (Getränk)
        "N710000",  # Früchtetee (Getränk)
        "N720100",  # Kräutertee (Getränk)
        "N721000",  # Pfefferminztee (Getränk)
        "N601000",  # Rooibos-Tee (Getränk)
        "N310400",  # Zitronenlimonade
        "N310300",  # Orangenlimonade
        "N330000",  # Colagetränk koffeinhaltig
        "N340000",  # Colagetränk koffeinfrei
        "N019000",  # Eistee, gesüßt
        "N4A0000",  # Kaffee mit Haferdrink, ungesüßt
        "N4A1000",  # Kaffee mit Mandeldrink, ungesüßt
        "N4A2000",  # Kaffee mit Sojadrink, ungesüßt
        "C650000",  # Reisdrink ungesüßt
        "C660000",  # Haferdrink ungesüßt
        "H800000",  # Mandeldrink ungesüßt
        "H841100",  # Sojadrink ungesüßt
    ],
    "Würzen & Grundnahrung": [
        "R111000",  # Speisesalz/Siedesalz/Tafelsalz
        "R114000",  # Speisesalz jodiert/Jodsalz
        "R112000",  # Meersalz
        "R258100",  # Pfeffer schwarz, getrocknet
        "R231000",  # Basilikum getrocknet
        "R123100",  # Apfelessig
        "R121000",  # Weinessig
        "R125000",  # Balsamicoessig
        "R132000",  # Senf mittelscharf
        "R141100",  # Tomatenketchup
        "R143000",  # Sojasauce
        "R160000",  # Tomatenmark
        "R161200",  # Tomaten passiert/Tomatenpüree
        "R821000",  # Gemüse Bouillon/Brühe (vegan)
        "R458000",  # Backhefe getrocknet
        "R459000",  # Backhefe frisch
        "R421100",  # Backpulver
        "S111000",  # Zucker weiß
        "S112000",  # Zucker braun
        "S111100",  # Puderzucker
        "S114000",  # Vanillezucker
        "S115100",  # Traubenzucker (Dextrose/Glukose)
        "S118000",  # Fruchtzucker (Fruktose)
        "S151100",  # Ahornsirup
        "S170000",  # Tafelsüße Tabletten (Süßstoffe)
        "S171000",  # Tafelsüße Flüssigsüße
    ],
}

# label, BLS group letters, (keyword, max claims) — long tail of fruit and
# vegetables. The best candidate per keyword is the shortest name without a
# preparation marker (typically "<Gemüse>, roh").
CATEGORIES: list[tuple[str, str, list[tuple[str, int]]]] = [
    (
        "Obst & Säfte",
        "F",
        [
            ("apfel", 1), ("birne", 1), ("banane", 1), ("pfirsich", 1),
            ("nektarine", 1), ("aprikose", 1), ("pflaume", 1), ("kirsche", 1),
            ("traube", 1), ("zitrone", 1), ("limette", 1), ("orange", 1),
            ("mandarine", 1), ("kiwi", 1), ("mango", 1), ("ananas", 1),
            ("melone", 1), ("granatapfel", 1), ("avocado", 1), ("feige", 1),
            ("dattel", 1), ("himbeere", 1), ("brombeere", 1), ("erdbeere", 1),
            ("johannisbeere", 1), ("stachelbeere", 1), ("holunderbeere", 1),
            ("sanddorn", 1), ("physalis", 1), ("quitte", 1), ("heidelbeere", 1),
            ("maracuja", 1),
        ],
    ),
    (
        "Gemüse & Kräuter",
        "G",
        [
            ("tomate", 1), ("gurke", 1), ("paprika", 3),
            ("speisezwiebel", 1),
            ("frühlingszwiebel", 1), ("knoblauch", 1), ("karotte", 1),
            ("porree", 1), ("lauch", 1), ("sellerie", 2),
            ("spinat", 1), ("broccoli", 1), ("blumenkohl", 1), ("rotkohl", 1),
            ("weißkohl", 1), ("wirsing", 1), ("rosenkohl", 1),
            ("grünkohl", 1), ("chinakohl", 1), ("kohlrübe", 1),
            ("zucchini", 1), ("aubergine", 1), ("endivie", 1),
            ("radicchio", 1), ("rucola", 1), ("salat", 1), ("radieschen", 1),
            ("mangold", 1), ("spargel", 1), ("artischocke", 1), ("fenchel", 1),
            ("pastinake", 1), ("chicoree", 1), ("kresse", 1),
            ("petersilie", 1), ("schnittlauch", 1), ("basilikum", 1), ("rettich", 1),
        ],
    ),
]

# Name patterns that mark a processed/derived variant. A keyword never claims
# such a row (unless the keyword itself contains the marker) and falls back to
# it only when there is no other hit.
PREP_MARKERS = [
    "saft", "mus", "marmelade", "konfitüre", "kompott", "kuchen", "torte",
    "keks", "plätzchen", "riegel", "chips", "püree", "purée", "instant",
    "pulver", "sirup", "wein", "essig", "sauce", "soße", "suppe", "brühe",
    "frittiert", "gebraten", "gedünstet", "gegrillt", "gebacken", "gefüllt",
    "gratin", "auflauf", "eintopf", "tiefgefroren", "dosen", "geschält",
    "getrocknet", "eingeweicht", "mischung", "knödel", "kloß", "salat",
    "brei", "pudding", "müsli", "konserve", "abgetropft", "gesäuert",
]

# Non-vegan name patterns (case-insensitive). "milch" etc. would also drop
# coconut milk, so the ALLOW pattern is checked first.
NON_VEGAN_PATTERN = (
    r"milch|butter|sahne|rahm|quark|joghurt|käse|honig|molke|laktose"
    r"|schmand|crème|creme|mayonnaise|gelatine|fleisch|fisch|wurst|speck"
    r"|hühner|huhn|omelett|rind|schwein|lamm|kalb|truthahn|pute|geflügel"
    r"|ente\b|gans\b|lebertran|schmalz|talg|cappuccino"
    r"|Eigelb|Eiklar|Hühnerei|Rührei|\bEi\b|Eier\b|eihaltig"
)
VEGAN_ALLOW_PATTERN = (
    r"kokosmilch|kokosnussmilch|butterpilz|butterbohne|butternuss"
    r"|erdnussbutter|nussbutter|fleischersatz|pflanzendrink"
)


def is_vegan(name: str) -> bool:
    import re

    if re.search(VEGAN_ALLOW_PATTERN, name, re.IGNORECASE):
        return True
    return not re.search(NON_VEGAN_PATTERN, name, re.IGNORECASE)


# BLS-Datenbankjargon -> App-taugliche Namen. Fehlt ein Code hier, greift
# display_name() (siehe unten) mit der Standardnormalisierung.
NAME_OVERRIDES: dict[str, str] = {
    # Obst & Säfte
    "F552100": "Physalis",
    "F321100": "Rote Johannisbeere",
    # Brot
    "B311000": "Weizenbrot (Weißbrot)",
    # Getreide, Reis & Pseudogetreide
    "C131000": "Haferkörner",
    "C133000": "Haferflocken",
    "C341000": "Mais",
    "C351000": "Naturreis",
    "C352000": "Weißreis",
    "C353100": "Wildreis",
    "C359000": "Parboiled-Reis",
    "C118000": "Quinoa",
    "C119100": "Bulgur",
    "C119200": "Couscous",
    "C322000": "Buchweizen",
    "C332000": "Hirse",
    "C335000": "Amaranth",
    "C211000": "Weizenvollkornmehl",
    "C214100": "Weizenmehl Type 405",
    "C214200": "Weizenmehl Type 550",
    "C221000": "Roggenvollkornmehl",
    "C223100": "Roggenmehl Type 815",
    "C234100": "Dinkelmehl Type 812",
    "C235000": "Dinkenvollkornmehl",
    "C424000": "Buchweizenmehl",
    "C443000": "Maismehl",
    "C219300": "Hartweizengrieß",
    "C512000": "Müsli (ungesüßt)",
    "C515400": "Cornflakes (ungesüßt)",
    "C533000": "Popcorn (ungesüßt)",
    "C532700": "Reiswaffeln (ungesalzen)",
    "C559000": "Reisnudeln, roh",
    "C558000": "Seitan",
    # Nudeln & Teigwaren
    "E401000": "Nudeln (Hartweizen), roh",
    "E510000": "Vollkornnudeln, roh",
    "E612000": "Nudeln (glutenfrei), roh",
    "E632000": "Dinkelnudeln, roh",
    "E632200": "Dinkelvollkornnudeln, roh",
    "E811000": "Frische Nudeln, roh",
    "E605000": "Schupfnudeln, roh",
    # Kartoffeln & Pilze
    "K120100": "Kartoffel, roh",
    "K110132": "Kartoffel, gekocht",
    "K420100": "Süßkartoffel, roh",
    "K724100": "Shiitake",
    "K762400": "Nährhefe",
    # Hülsenfrüchte, Nüsse & Samen
    "G710100": "Grüne Bohne",
    "G730100": "Dicke Bohne (frisch)",
    "G750100": "Edamame",
    "G760100": "Grüne Erbse",
    "G760400": "Trockenerbse",
    "G770400": "Kichererbsen",
    "G780400": "Limabohne",
    "G750400": "Trockensojabohne",
    "H702100": "Augenbohnen",
    "H739400": "Gartenbohnen (trocken)",
    "H742100": "Kidneybohnen",
    "H750100": "Dicke Bohne (trocken)",
    "H725100": "Grüne Linse",
    "H730000": "Rote Linse",
    "H765000": "Sojaschnetzel (trocken)",
    "H960000": "Hummus",
    "H154000": "Kokosmilch",
    "H510802": "Grüne Oliven",
    "H030400": "Glasnudeln",
    "H110600": "Erdnüsse (geröstet)",
    "H120100": "Walnüsse",
    "H130100": "Haselnüsse",
    "H160100": "Pekannüsse",
    "H170100": "Cashewkerne",
    "H180100": "Paranüsse",
    "H190100": "Macadamianüsse",
    "H210100": "Mandeln",
    "H230100": "Edelkastanien",
    "H250100": "Pistazien",
    "H320100": "Pinienkerne",
    "H420100": "Sesam",
    "H430100": "Sonnenblumenkerne",
    "H480100": "Chiasamen",
    "H310100": "Kürbiskerne",
    "H880200": "Erdnussbutter",
    "Q901000": "Tahin",
    # Öle & Fette
    "Q180000": "Rapsöl",
    "Q400000": "Margarine (Vollfett)",
    "Q440000": "Margarine (Halbfett)",
    # Getränke
    "N120000": "Mineralwasser",
    "N127000": "Mineralwasser (mit Kohlensäure)",
    "N128000": "Mineralwasser (still)",
    "N410100": "Kaffee",
    "N420100": "Kaffee (instant)",
    "N411100": "Espresso",
    "N415000": "Kaffee (entkoffeiniert)",
    "N610100": "Grüner Tee",
    "N630000": "Schwarzer Tee",
    "N601000": "Rooibos-Tee",
    "N330000": "Cola",
    "N340000": "Cola (koffeinfrei)",
    "N019000": "Eistee (gesüßt)",
    "N4A0000": "Kaffee mit Haferdrink",
    "N4A1000": "Kaffee mit Mandeldrink",
    "N4A2000": "Kaffee mit Sojadrink",
    "C650000": "Reisdrink",
    "C660000": "Haferdrink",
    "H800000": "Mandeldrink",
    "H841100": "Sojadrink",
    # Würzen & Grundnahrung
    "R111000": "Speisesalz",
    "R114000": "Jodsalz",
    "R258100": "Schwarzer Pfeffer",
    "R231000": "Basilikum (getrocknet)",
    "R132000": "Senf (mittelscharf)",
    "R141100": "Ketchup",
    "R143000": "Sojasauce",
    "R161200": "Passierte Tomaten",
    "R821000": "Gemüsebrühe (Bouillon)",
    "R458000": "Trockenhefe",
    "R459000": "Frischhefe",
    "S111000": "Zucker (weiß)",
    "S112000": "Brauner Zucker",
    "S115100": "Traubenzucker",
    "S118000": "Fruchtzucker",
    "S170000": "Süßstofftabletten",
    "S171000": "Flüssigsüßstoff",
    # Gemüse & Kräuter
    "G543100": "Paprika (rot)",
    "G542100": "Paprika (gelb)",
    "G541100": "Paprika (grün)",
    "G480100": "Zwiebel",
    "G482100": "Frühlingszwiebel",
    "G620100": "Karotten",
    "G470100": "Porree",
    "G220100": "Stangensellerie",
    "G312100": "Brokkoli",
    "G611100": "Kohlrabi",
    "G102100": "Endivie",
    "G430100": "Fenchel",
    "G280100": "Kresse",
    "G250100": "Petersilie",
    "G105100": "Kopfsalat",
    "G343100": "Wirsing (Savoykohl)",
}


def display_name(code: str, bls_name: str) -> str:
    """BLS-Name -> App-tauglicher Name (override, sonst Standardnormalisierung)."""
    if code in NAME_OVERRIDES:
        return NAME_OVERRIDES[code]
    # frisches Obst/Gemüse: ", roh" ist implizit
    if code[:1] in "FG" and bls_name.endswith(", roh"):
        return bls_name[: -len(", roh")]
    return bls_name
