#!/usr/bin/env python3
"""Build Midati's generic food catalogue from pinned Open Food Facts, FoodOn and recipes.

This catalogue is for generic foods. Brands, barcodes, retail pack sizes and prices
belong to the separate market-products/pricing system.
"""
from __future__ import annotations
import argparse, csv, hashlib, json, re, sys, time, urllib.error, urllib.request, uuid, unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RECIPES = ROOT / "data/recipes/ar/recipes.json"
DEFAULT_OUTPUT = ROOT / "data/catalogues"
SOURCE_LOCK = ROOT / "data/catalogues/source-lock.json"
CURATED_FILE = ROOT / "data/catalogues/curated-food-items.json"
REGISTRY_NAME = "food-item-id-registry.json"

UNIT_ORDER = ["g","kg","ml","l","piece","clove","slice","can","tbsp","tsp","cup","sprig","bunch","pinch","handful","to_taste"]
UNIT_ALIASES = {
    "gram":"g","grams":"g","gramme":"g","grammes":"g","kilogram":"kg","kilograms":"kg",
    "milliliter":"ml","milliliters":"ml","millilitre":"ml","millilitres":"ml",
    "liter":"l","liters":"l","litre":"l","litres":"l","pc":"piece","pcs":"piece","pieces":"piece",
    "unit":"piece","units":"piece","cloves":"clove","slices":"slice","cans":"can",
    "tablespoon":"tbsp","tablespoons":"tbsp","tbs":"tbsp","tbsps":"tbsp",
    "teaspoon":"tsp","teaspoons":"tsp","tsps":"tsp","cups":"cup","sprigs":"sprig",
    "bunches":"bunch","handfuls":"handful","totaste":"to_taste","taste":"to_taste","to taste":"to_taste","":"piece"
}

# Application-owned categories: external taxonomy versions can change without
# changing Midati category IDs.
CATEGORY_TREE = [
 {"id":"fruits","names":{"ar":"الفواكه","en":"Fruits","fr":"Fruits"},"subs":[
  ("citrus-fruits","الحمضيات","Citrus fruits","Agrumes"),("berries","التوتيات","Berries","Baies"),
  ("stone-fruits","الفواكه ذات النواة","Stone fruits","Fruits à noyau"),("tropical-fruits","الفواكه الاستوائية","Tropical fruits","Fruits tropicaux"),("dried-fruits","الفواكه المجففة","Dried fruits","Fruits séchés")]},
 {"id":"vegetables","names":{"ar":"الخضروات","en":"Vegetables","fr":"Légumes"},"subs":[
  ("leafy-vegetables","الخضروات الورقية","Leafy vegetables","Légumes-feuilles"),("roots-tubers","الجذور والدرنات","Roots and tubers","Racines et tubercules"),
  ("bulbs-stems","الأبصال والسيقان","Bulbs and stems","Bulbes et tiges"),("fruit-vegetables","الخضروات الثمرية","Fruit vegetables","Légumes-fruits"),
  ("canned-vegetables","الخضروات المعلبة","Canned vegetables","Légumes en conserve"),("frozen-vegetables","الخضروات المجمدة","Frozen vegetables","Légumes surgelés")]},
 {"id":"meat-offal","names":{"ar":"اللحوم والأحشاء","en":"Meat and offal","fr":"Viandes et abats"},"subs":[
  ("red-meat","اللحوم الحمراء","Red meat","Viande rouge"),("offal","الأحشاء","Offal","Abats"),("processed-meat","اللحوم المصنعة","Processed meat","Viandes transformées")]},
 {"id":"poultry","names":{"ar":"الدواجن","en":"Poultry","fr":"Volailles"},"subs":[
  ("chicken","الدجاج","Chicken","Poulet"),("turkey","الديك الرومي","Turkey","Dinde"),("other-poultry","دواجن أخرى","Other poultry","Autres volailles")]},
 {"id":"seafood","names":{"ar":"المأكولات البحرية","en":"Seafood","fr":"Produits de la mer"},"subs":[
  ("fish","الأسماك","Fish","Poissons"),("shellfish","القشريات والرخويات","Shellfish and molluscs","Crustacés et mollusques"),("canned-fish","الأسماك المعلبة","Canned fish","Poissons en conserve")]},
 {"id":"dairy","names":{"ar":"الألبان ومشتقاتها","en":"Dairy and fermented products","fr":"Produits laitiers"},"subs":[
  ("fresh-milk","الحليب","Milk","Lait"),("fermented-dairy","الألبان المخمرة","Fermented dairy","Laits fermentés"),("cream","القشدة","Cream","Crème"),("dairy-powders","مساحيق الحليب","Dairy powders","Poudres laitières")]},
 {"id":"cheese-butter","names":{"ar":"الأجبان والزبدة","en":"Cheese and butter","fr":"Fromages et beurre"},"subs":[
  ("soft-cheese","الأجبان الطرية","Soft cheese","Fromages frais"),("hard-cheese","الأجبان الصلبة","Hard cheese","Fromages à pâte dure"),("butter-margarine","الزبدة والمارغرين","Butter and margarine","Beurre et margarine")]},
 {"id":"eggs","names":{"ar":"البيض","en":"Eggs","fr":"Œufs"},"subs":[("bird-eggs","بيض الطيور","Bird eggs","Œufs d’oiseaux")]},
 {"id":"legumes","names":{"ar":"البقوليات","en":"Legumes","fr":"Légumineuses"},"subs":[
  ("beans","الفاصولياء والفول","Beans and fava beans","Haricots et fèves"),("chickpeas","الحمص","Chickpeas","Pois chiches"),("lentils","العدس","Lentils","Lentilles"),
  ("peas","البازلاء","Peas","Pois"),("canned-legumes","البقوليات المعلبة","Canned legumes","Légumineuses en conserve")]},
 {"id":"grains-cereals","names":{"ar":"الحبوب والمنتجات النشوية","en":"Grains and cereals","fr":"Céréales"},"subs":[
  ("wheat-semolina","القمح والسميد","Wheat and semolina","Blé et semoule"),("rice","الأرز","Rice","Riz"),("other-grains","حبوب أخرى","Other grains","Autres céréales")]},
 {"id":"pasta-bakery","names":{"ar":"العجائن والخبز","en":"Pasta and bakery","fr":"Pâtes et boulangerie"},"subs":[
  ("pasta","العجائن","Pasta","Pâtes"),("bread-bakery","الخبز والمخبوزات","Bread and bakery","Pain et produits boulangers")]},
 {"id":"nuts-seeds","names":{"ar":"المكسرات والبذور","en":"Nuts and seeds","fr":"Noix et graines"},"subs":[
  ("tree-nuts","المكسرات","Tree nuts","Fruits à coque"),("edible-seeds","البذور الصالحة للأكل","Edible seeds","Graines comestibles")]},
 {"id":"oils-fats","names":{"ar":"الزيوت والدهون","en":"Oils and fats","fr":"Huiles et matières grasses"},"subs":[
  ("vegetable-oils","الزيوت النباتية","Vegetable oils","Huiles végétales"),("animal-fats","الدهون الحيوانية","Animal fats","Graisses animales")]},
 {"id":"spices","names":{"ar":"التوابل والبهارات","en":"Spices","fr":"Épices"},"subs":[
  ("ground-spices","التوابل المطحونة","Ground spices","Épices moulues"),("whole-spices","التوابل الكاملة","Whole spices","Épices entières"),("spice-blends","خلطات التوابل","Spice blends","Mélanges d’épices")]},
 {"id":"herbs","names":{"ar":"الأعشاب العطرية","en":"Herbs","fr":"Herbes aromatiques"},"subs":[
  ("fresh-herbs","الأعشاب الطازجة","Fresh herbs","Herbes fraîches"),("dried-herbs","الأعشاب المجففة","Dried herbs","Herbes séchées")]},
 {"id":"sauces-condiments","names":{"ar":"الصلصات والمنكهات","en":"Sauces and condiments","fr":"Sauces et condiments"},"subs":[
  ("mayonnaise-dressings","المايونيز والتتبيلات","Mayonnaise and dressings","Mayonnaise et vinaigrettes"),("tomato-sauces","صلصات الطماطم","Tomato sauces","Sauces tomate"),("mustard-condiments","الخردل والمنكهات","Mustard and condiments","Moutardes et condiments")]},
 {"id":"sweeteners","names":{"ar":"المحليات","en":"Sweeteners","fr":"Édulcorants"},"subs":[
  ("sugar-syrups","السكر والشراب","Sugar and syrups","Sucres et sirops"),("honey-jam","العسل والمربى","Honey and jam","Miel et confiture")]},
 {"id":"baking-desserts","names":{"ar":"مكونات الخَبز والحلويات","en":"Baking and dessert ingredients","fr":"Ingrédients de pâtisserie"},"subs":[
  ("leavening","مواد الرفع والتخمير","Leavening agents","Agents levants"),("baking-chocolate","الشوكولاتة والكاكاو","Chocolate and cocoa","Chocolat et cacao"),("dessert-mixes","خلطات الحلويات","Dessert mixes","Préparations pour desserts")]},
 {"id":"beverages","names":{"ar":"المشروبات","en":"Beverages","fr":"Boissons"},"subs":[
  ("juices","العصائر","Juices","Jus"),("hot-beverages","المشروبات الساخنة","Hot beverages","Boissons chaudes"),("water","المياه","Water","Eaux")]},
 {"id":"frozen-foods","names":{"ar":"الأغذية المجمدة","en":"Frozen foods","fr":"Produits surgelés"},"subs":[
  ("frozen-meals","الوجبات المجمدة","Frozen meals","Plats surgelés"),("frozen-desserts","الحلويات المجمدة","Frozen desserts","Desserts glacés")]},
 {"id":"mushrooms-fungi","names":{"ar":"الفطريات والفطر","en":"Mushrooms and fungi","fr":"Champignons"},"subs":[("edible-mushrooms","الفطر الصالح للأكل","Edible mushrooms","Champignons comestibles")]},
 {"id":"other-foods","names":{"ar":"مواد غذائية أخرى","en":"Other foods","fr":"Autres aliments"},"subs":[]}
]
SUBCATEGORY_PARENT = {s[0]: c["id"] for c in CATEGORY_TREE for s in c["subs"]}
PREPARATION_WORDS = {"cooked","uncooked","raw","boiled","fried","baked","grilled","roasted","steamed","chopped","diced","sliced","minced","grated","peeled","shredded","skinless","boneless","seeded","pitted","crushed","mashed","pureed","puréed"}
FOOD_ADDITIVE_HINTS = {"acid","starch","gum","extract","lecithin","emulsifier","stabilizer","thickener","preservative","colour","color","flavour","flavor","aroma","gelatin","pectin","carrageenan","baking soda","baking powder","raising agent","e-number"}
LIQUID_HINTS = {"milk","lben","leben","raib","yogurt","yoghurt","kefir","juice","water","oil","vinegar","sauce","syrup","cream","beverage","drink"}
SOLID_COUNT_HINTS = {"egg","eggs","onion","garlic","tomato","potato","pepper","fruit","apple","banana"}

def normalize_term(value: str) -> str:
    value = unicodedata.normalize("NFKD", str(value or "")).casefold()
    value = "".join(ch for ch in value if not unicodedata.combining(ch)).replace("ـ","")
    value = re.sub(r"[\u064B-\u065F\u0670]","",value)
    value = value.translate(str.maketrans({"أ":"ا","إ":"ا","آ":"ا","ٱ":"ا","ى":"ي"})).replace("&"," and ")
    return re.sub(r"\s+"," ",re.sub(r"[^\w]+"," ",value,flags=re.UNICODE)).strip()

def split_aliases(value: str) -> list[str]:
    return [v.strip() for v in str(value or "").split(",") if v.strip()]

def country_names(raw: str) -> list[str]:
    result=[]
    for value in split_aliases(raw):
        value=re.sub(r"^[a-z]{2,3}:","",value.strip(),flags=re.I)
        value=re.sub(r"^[a-z]{2,3}:","",value.strip(),flags=re.I)
        if value: result.append(value.replace("_"," ").replace("-"," ").title())
    return result

def parse_off_taxonomy(text: str) -> list[dict[str,Any]]:
    entries={}
    for block in re.split(r"\n\s*\n",text):
        fields=defaultdict(list); parents=[]; origins=[]
        for raw in block.splitlines():
            line=raw.strip()
            if not line or line.startswith("#"): continue
            p=re.match(r"^<\s*en\s*:\s*(.+)$",line,re.I)
            if p: parents.extend(split_aliases(p.group(1))); continue
            o=re.match(r"^(?:origin|origins|country_of_origin):[a-z-]+:\s*(.*)$",line,re.I)
            if o: origins.extend(country_names(o.group(1))); continue
            m=re.match(r"^([a-z]{2,3}(?:-[a-z0-9]+)?)\s*:\s*(.*)$",line,re.I)
            if m: fields[m.group(1).lower()].append(m.group(2).strip())
        english=[a for part in fields.get("en",[]) for a in split_aliases(part)]
        if not english: continue
        canonical=english[0]; key=normalize_term(canonical)
        if not key: continue
        item=entries.setdefault(key,{"name_en":canonical,"names":{"ar":[],"en":[],"fr":[]},"aliases":{"ar":[],"en":[],"fr":[]},"parents":[],"image_url":None,"country_of_origin":None,"nutrition":None,"source_keys":set()})
        for lang in ("ar","en","fr"):
            vals=[a for part in fields.get(lang,[]) for a in split_aliases(part)]
            for value in vals:
                target=item["names"][lang] if not item["names"][lang] else item["aliases"][lang]
                if value not in target: target.append(value)
        for parent in parents:
            if parent not in item["parents"]: item["parents"].append(parent)
        if origins: item["country_of_origin"]=sorted(set((item["country_of_origin"] or [])+origins))
        item["source_keys"].add("off:"+key)
    return list(entries.values())

def _foodon_literal(raw: str) -> tuple[str,str]:
    value=raw.strip()
    m=re.match(r"^(.*)@([a-z0-9-]+)$",value,re.I)
    return (m.group(1).strip(),m.group(2).lower()) if m else (value,"en")

def parse_foodon_tsv(text: str) -> tuple[dict[str,dict[str,Any]],dict[str,list[str]]]:
    nodes={}; parents=defaultdict(list)
    for row in csv.reader(text.splitlines(),delimiter="\t",quotechar='"'):
        if len(row)<4 or row[0]=="?class": continue
        sm=re.search(r"/obo/([A-Z]+_\d+)>",row[0])
        if not sm: continue
        cid=sm.group(1); pm=re.search(r"/obo/([A-Z]+_\d+)>",row[1])
        if pm and pm.group(1) not in parents[cid]: parents[cid].append(pm.group(1))
        kind=row[2].strip().strip('"').lower()
        value,lang=_foodon_literal(row[3])
        if value.startswith('"') and value.endswith('"'): value=value[1:-1]
        value=value.replace('\\"','"').strip()
        if not value: continue
        node=nodes.setdefault(cid,{"id":cid.replace("_",":",1),"names":defaultdict(list),"aliases":defaultdict(list),"parents":[]})
        if kind in ("label","label (alternative)"):
            target=node["names"] if not node["names"][lang] else node["aliases"]
            if value not in target[lang]: target[lang].append(value)
        elif kind in ("synonym (exact)","synonym"):
            if value not in node["aliases"][lang]: node["aliases"][lang].append(value)
    for cid,node in nodes.items(): node["parents"]=parents.get(cid,[])
    return nodes,parents

def foodon_product_classes(nodes,parents):
    children=defaultdict(list)
    for child,ps in parents.items():
        for parent in ps: children[parent].append(child)
    root="FOODON_00001002"; reachable=set(); stack=[root]
    while stack:
        current=stack.pop()
        if current in reachable: continue
        reachable.add(current); stack.extend(children.get(current,[]))
    return {cid for cid in reachable if cid.startswith("FOODON_") and cid in nodes and nodes[cid]["names"].get("en")}

def foodon_ancestor_labels(cid,nodes,parents):
    out=[]; seen=set(); stack=list(parents.get(cid,[]))
    while stack:
        current=stack.pop()
        if current in seen: continue
        seen.add(current)
        if current in nodes: out.extend(nodes[current]["names"].get("en",[]))
        stack.extend(parents.get(current,[]))
    return out

def classify_food(values: list[str]) -> tuple[str,str|None]:
    n=normalize_term(" ".join(str(v) for v in values if v)); words=set(n.split())
    has=lambda *terms:any(t in n for t in terms)
    word=lambda *terms:bool(words.intersection(terms))
    canned=has("canned","tinned","in conserve","en conserve","preserved")
    frozen=has("frozen","surgel")
    if has("mayonnaise","ketchup","mustard","sauce","condiment","dressing","vinegar"):
        return "sauces-condiments","mayonnaise-dressings" if has("mayonnaise","mayo","dressing") else "tomato-sauces" if has("tomato") else "mustard-condiments"
    if word("oil","oils") or has("olive oil","sunflower oil","vegetable oil","rapeseed oil","lard","tallow"):
        return "oils-fats","animal-fats" if has("animal","lard","tallow") else "vegetable-oils"
    if has("mushroom","fungus","fungi","truffle"): return "mushrooms-fungi","edible-mushrooms"
    if has("shellfish","mollusc","mollusk","crustacean","shrimp","prawn","lobster","crab","oyster","clam","mussel"):
        return "seafood","shellfish"
    if word("fish") or has("tuna","salmon","sardine","anchovy","cod","mackerel","herring"):
        return "seafood","canned-fish" if canned else "fish"
    if has("poultry","chicken","turkey","duck","goose"):
        return "poultry","turkey" if has("turkey") else "chicken" if has("chicken") else "other-poultry"
    if has("offal","liver","kidney","heart","tripe","tongue"):
        return "meat-offal","offal"
    if has("meat","beef","lamb","mutton","veal","pork","sausage","ham","bacon","frankfurter"):
        return "meat-offal","processed-meat" if has("sausage","ham","bacon","frankfurter") else "red-meat"
    if word("egg","eggs"): return "eggs","bird-eggs"
    if has("cheese","fromage"):
        return "cheese-butter","hard-cheese" if has("hard","aged","cheddar","parmesan") else "soft-cheese"
    if has("butter","margarine"): return "cheese-butter","butter-margarine"
    if has("chocolate","cocoa"): return "baking-desserts","baking-chocolate"
    if has("dairy","milk","lben","leben","raib","yogurt","yoghurt","yoghourt","kefir","fermented milk","cream","lait"):
        if has("yogurt","yoghurt","yoghourt","kefir","lben","leben","raib","fermented"): return "dairy","fermented-dairy"
        if has("powder","formula"): return "dairy","dairy-powders"
        if has("cream"): return "dairy","cream"
        return "dairy","fresh-milk"
    if has("juice","beverage","drink","coffee","tea","water"):
        return "beverages","juices" if has("juice") else "water" if has("water") else "hot-beverages"
    if has("citrus","orange","lemon","lime","grapefruit","mandarin"): return "fruits","citrus-fruits"
    if has("berry","berries","strawberry","blueberry","raspberry","blackberry"): return "fruits","berries"
    if has("dried fruit","raisin","date","fig","prune","dried apricot"): return "fruits","dried-fruits"
    if word("fruit") or has("apple","banana","mango","pineapple","peach","pear","plum","grape","melon","watermelon"):
        sub="tropical-fruits" if has("mango","pineapple","banana","papaya","coconut") else "stone-fruits" if has("peach","plum","apricot","cherry") else None
        return "fruits",sub
    if has("leafy","lettuce","spinach","cabbage","chard","kale"): return "vegetables","leafy-vegetables"
    if has("potato","carrot","beet","turnip","radish","yam","tuber","root vegetable"): return "vegetables","roots-tubers"
    if has("onion","garlic","leek","shallot","bulb"): return "vegetables","bulbs-stems"
    if has("bell pepper","sweet pepper","capsicum","red pepper","green pepper","tomato","cucumber","zucchini","courgette","eggplant","aubergine","okra","squash"):
        return "vegetables","frozen-vegetables" if frozen else "canned-vegetables" if canned else "fruit-vegetables"
    if has("chickpea","garbanzo"): return "legumes","canned-legumes" if canned else "chickpeas"
    if has("lentil"): return "legumes","canned-legumes" if canned else "lentils"
    if word("pea","peas"): return "legumes","canned-legumes" if canned else "peas"
    if has("bean","fava","pulse","legume","soybean","soya"): return "legumes","canned-legumes" if canned else "beans"
    if has("nut","almond","walnut","hazelnut","pistachio","cashew","peanut"): return "nuts-seeds","tree-nuts"
    if has("seed","sesame","sunflower seed","pumpkin seed","chia"): return "nuts-seeds","edible-seeds"
    if has("spice","cumin","paprika","cinnamon","turmeric","ginger","coriander seed","clove","nutmeg","saffron","curry") or has("black pepper","white pepper","peppercorn"):
        return "spices","spice-blends" if has("blend","curry powder","seasoning mix") else "ground-spices" if has("ground","powder") else "whole-spices"
    if has("herb","parsley","cilantro","coriander leaf","thyme","basil","mint","oregano","rosemary","dill"):
        return "herbs","dried-herbs" if has("dried") else "fresh-herbs"
    if has("sugar","honey","syrup","jam","molasses","sweetener"): return "sweeteners","honey-jam" if has("honey","jam") else "sugar-syrups"
    if has("pasta","noodle","macaroni","spaghetti"): return "pasta-bakery","pasta"
    if has("bread","bakery","biscuit","cracker","pastry"): return "pasta-bakery","bread-bakery"
    if has("flour","semolina","wheat","rice","cereal","grain","oat","barley","corn","maize"):
        return "grains-cereals","rice" if word("rice") else "wheat-semolina" if has("wheat","semolina","flour") else "other-grains"
    if frozen: return "frozen-foods","frozen-meals"
    return "other-foods",None

def default_units(name_en: str,category_id: str) -> set[str]:
    n=normalize_term(name_en); words=set(n.split())
    if category_id=="eggs": return {"piece"}
    if any(h in n for h in LIQUID_HINTS) or category_id=="beverages": return {"ml","l"}
    if words.intersection(SOLID_COUNT_HINTS): return {"g","kg","piece"}
    return {"g","kg"}

def clean_recipe_ingredient(ingredient: dict[str,Any],recipe_id: str) -> str:
    source=str(ingredient.get("sourceNameEn") or "").strip()
    if recipe_id.startswith("focus:"):
        source=str(ingredient.get("sourceTextEn") or source).strip()
        if ":" in source: source=source.split(":",1)[0].strip()
    if not source: source=str(ingredient.get("name") or "").strip()
    source=re.sub(r"\([^)]*\)"," ",source); source=re.sub(r"\s+"," ",source).strip(" ,;")
    parts=[p.strip() for p in source.split(",") if p.strip()]
    if parts: source=", ".join([parts[0]]+[p for p in parts[1:] if normalize_term(p) not in PREPARATION_WORDS])
    for word in PREPARATION_WORDS: source=re.sub(r"\b"+re.escape(word)+r"\b"," ",source,flags=re.I)
    return re.sub(r"\s+"," ",source).strip(" ,;-") or str(ingredient.get("sourceNameEn") or ingredient.get("name") or "").strip()

def normalize_unit(value: str) -> str:
    raw=re.sub(r"\s+"," ",str(value or "").strip().casefold().replace("_"," ").replace("-"," "))
    return UNIT_ALIASES.get(raw,raw.replace(" ","_") if raw else "piece")

def add_value(values: list[str],value: str|None) -> None:
    if value and value not in values: values.append(value)

def new_record(name_en: str,source_key: str,category_values: list[str]|None=None) -> dict[str,Any]:
    category,sub=classify_food(category_values or [name_en])
    return {"name_en":name_en,"names":{"ar":[],"en":[name_en],"fr":[]},"aliases":{"ar":[],"en":[],"fr":[]},
            "accepted_units":set(),"image_url":None,"country_of_origin":None,"nutrition":None,
            "category_id":category,"subcategory_id":sub,"foodon_ids":set(),"source_keys":{source_key}}

def add_aliases_from_entry(record,entry):
    for lang in ("ar","en","fr"):
        for value in entry.get("names",{}).get(lang,[]):
            if value not in record["names"][lang]: add_value(record["aliases"][lang],value)
        for value in entry.get("aliases",{}).get(lang,[]): add_value(record["aliases"][lang],value)

def load_source_text(local_path: Path|None,repo: str,commit: str,path: str,expected_sha: str) -> str:
    if local_path: data=local_path.read_bytes()
    else:
        request=urllib.request.Request(f"https://raw.githubusercontent.com/{repo}/{commit}/{path}",headers={"User-Agent":"Midati-Food-Catalogue-Builder/1.0"})
        last=None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(request,timeout=180) as response: data=response.read()
                break
            except (urllib.error.URLError,TimeoutError,OSError) as error:
                last=error
                if attempt<2: time.sleep(2**attempt)
        else: raise RuntimeError(f"Could not download {repo}/{path}: {last}")
    actual=hashlib.sha1(f"blob {len(data)}\0".encode("ascii")+data).hexdigest()
    if expected_sha and actual.lower()!=expected_sha.lower(): raise RuntimeError(f"Source checksum mismatch for {repo}/{path}: {actual}")
    return data.decode("utf-8-sig")

def build_categories():
    result=[]
    for cat in CATEGORY_TREE:
        result.append({"id":cat["id"],"names":cat["names"],"parentId":None})
        for sid,ar,en,fr in cat["subs"]: result.append({"id":sid,"names":{"ar":ar,"en":en,"fr":fr},"parentId":cat["id"]})
    return result

def make_alias_index(records):
    index=defaultdict(set)
    for pos,record in enumerate(records):
        for lang in ("ar","en","fr"):
            for value in record["names"].get(lang,[])+record["aliases"].get(lang,[]):
                key=normalize_term(value)
                if key: index[key].add(pos)
    return index

def assign_stable_ids(records,registry):
    ids=registry.setdefault("ids",{}); redirects=registry.setdefault("redirects",{}); output={}
    for record in records:
        keys=sorted(record["source_keys"]); previous=sorted({ids[k] for k in keys if k in ids})
        if previous:
            item_id=previous[0]
            for old in previous[1:]: redirects[old]=item_id
        else: item_id=str(uuid.uuid4())
        for key in keys: ids[key]=item_id
        output[record["internal_key"]]=item_id
    for old,target in list(redirects.items()):
        seen=set()
        while target in redirects and target not in seen: seen.add(target); target=redirects[target]
        redirects[old]=target
    return output

def clean_output_record(record,item_id):
    names={}; aliases={}
    for lang in ("ar","en","fr"):
        primary=record["names"][lang][0] if record["names"][lang] else None
        names[lang]=primary; primary_key=normalize_term(primary or ""); unique={}
        for value in record["names"][lang][1:]+record["aliases"][lang]:
            key=normalize_term(value)
            if key and key!=primary_key and key not in unique: unique[key]=value
        aliases[lang]=[unique[k] for k in sorted(unique)]
    units=record["accepted_units"] or default_units(record["name_en"],record["category_id"])
    return {"id":item_id,"names":names,"acceptedUnits":sorted(units,key=lambda u:(UNIT_ORDER.index(u) if u in UNIT_ORDER else len(UNIT_ORDER),u)),
            "imageUrl":record["image_url"],"categoryId":record["category_id"],"subcategoryId":record["subcategory_id"],
            "aliases":aliases,"countryOfOrigin":record["country_of_origin"],"nutrition":record["nutrition"],
            "externalIds":{"foodOn":sorted(record["foodon_ids"])}}

def build_catalogue(recipes_data,off_entries,foodon_nodes,foodon_parents,curated_items,registry):
    if recipes_data.get("complete") is not True or recipes_data.get("translatedCount")!=recipes_data.get("expectedCount"):
        raise RuntimeError("Recipe source catalogue is incomplete; refusing to build food catalogue.")
    recipe_list=recipes_data.get("recipes") or []
    if len(recipe_list)!=int(recipes_data.get("expectedCount") or -1): raise RuntimeError("Recipe source count does not match expectedCount.")
    records=[]; canonical_index={}; off_excluded=0
    for entry in off_entries:
        name=entry["name_en"]; norm=normalize_term(name)
        if not norm: continue
        cat,_=classify_food([name]+entry.get("parents",[]))
        if cat=="other-foods" and not entry.get("parents") and not any(h in norm for h in FOOD_ADDITIVE_HINTS):
            off_excluded+=1; continue
        if norm in canonical_index: target=records[canonical_index[norm]]
        else:
            target=new_record(name,"off:"+norm,[name]+entry.get("parents",[])); target["internal_key"]="off:"+norm
            canonical_index[norm]=len(records); records.append(target)
        add_aliases_from_entry(target,entry); target["source_keys"].update(entry.get("source_keys",[]))
        candidate=classify_food([name]+entry.get("parents",[]))
        if target["category_id"]=="other-foods" or candidate[0]!="other-foods": target["category_id"],target["subcategory_id"]=candidate
        target["image_url"]=target["image_url"] or entry.get("image_url")
        target["country_of_origin"]=target["country_of_origin"] or entry.get("country_of_origin")
        target["nutrition"]=target["nutrition"] or entry.get("nutrition")

    off_alias_index=make_alias_index(records)
    product_classes=foodon_product_classes(foodon_nodes,foodon_parents)
    foodon_added=foodon_matched=foodon_ambiguous=0; foodon_by_name={}
    for cid in sorted(product_classes):
        node=foodon_nodes[cid]; names=node["names"].get("en",[])
        if not names: continue
        name=names[0]; norm=normalize_term(name)
        if not norm or norm in {"food product","foodon product type"}: continue
        alias_norms={normalize_term(v) for lang in ("ar","en","fr") for v in node["names"].get(lang,[])+node["aliases"].get(lang,[]) if normalize_term(v)}
        candidates=set()
        for alias in alias_norms: candidates.update(off_alias_index.get(alias,set()))
        if len(candidates)==1:
            target_index=next(iter(candidates)); target=records[target_index]; foodon_matched+=1
        elif len(candidates)>1:
            foodon_ambiguous+=1
            if norm not in canonical_index: continue
            target_index=canonical_index[norm]; target=records[target_index]
        elif norm in canonical_index:
            target_index=canonical_index[norm]; target=records[target_index]; foodon_matched+=1
        elif norm in foodon_by_name:
            target_index=foodon_by_name[norm]; target=records[target_index]; foodon_matched+=1
        else:
            target=new_record(name,"foodon:"+cid,[name]+foodon_ancestor_labels(cid,foodon_nodes,foodon_parents))
            target["internal_key"]="foodon:"+cid; target_index=len(records)
            records.append(target); foodon_added+=1
        foodon_by_name.setdefault(norm,target_index); canonical_index.setdefault(norm,target_index)
        target["source_keys"].add("foodon:"+cid); target["foodon_ids"].add(cid.replace("_",":",1))
        for lang in ("ar","en","fr"):
            for value in node["names"].get(lang,[]):
                if value not in target["names"][lang]: add_value(target["aliases"][lang],value)
            for value in node["aliases"].get(lang,[]): add_value(target["aliases"][lang],value)
        candidate=classify_food([name]+foodon_ancestor_labels(cid,foodon_nodes,foodon_parents))
        if target["category_id"]=="other-foods" and candidate[0]!="other-foods": target["category_id"],target["subcategory_id"]=candidate

    curated_added=0
    for entry in curated_items:
        name=str(entry.get("names",{}).get("en") or "").strip()
        if not name: raise RuntimeError("Curated food item is missing its English name.")
        norm=normalize_term(name); idx=canonical_index.get(norm)
        if idx is None:
            target=new_record(name,"curated:"+str(entry["key"]),[name]); target["internal_key"]="curated:"+str(entry["key"])
            idx=len(records); canonical_index[norm]=idx; records.append(target); curated_added+=1
        else: target=records[idx]
        for lang in ("ar","en","fr"):
            value=entry.get("names",{}).get(lang)
            if value:
                if not target["names"][lang]: target["names"][lang].append(value)
                else: add_value(target["aliases"][lang],value)
            for alias in entry.get("aliases",{}).get(lang,[]): add_value(target["aliases"][lang],str(alias))
        target["accepted_units"].update(normalize_unit(v) for v in entry.get("acceptedUnits",[]))
        target["source_keys"].add("curated:"+str(entry["key"]))
        target["category_id"]=entry.get("categoryId") or target["category_id"]
        target["subcategory_id"]=entry.get("subcategoryId") or target["subcategory_id"]

    exact_index=make_alias_index(records); name_index=dict(canonical_index); by_recipe_term=defaultdict(list)
    for recipe in recipe_list:
        rid=str(recipe.get("id") or "")
        for i,ingredient in enumerate(recipe.get("ingredients") or []):
            cleaned=clean_recipe_ingredient(ingredient,rid); norm=normalize_term(cleaned)
            if not norm: raise RuntimeError(f"Could not normalize ingredient {i} in {rid}.")
            by_recipe_term[norm].append((rid,i,ingredient,cleaned))
    rows=[]; recipe_off=recipe_foodon=recipe_only=ambiguous=0
    for norm,occurrences in sorted(by_recipe_term.items()):
        cleaned=occurrences[0][3]; candidates=set(exact_index.get(norm,set()))
        if len(candidates)==1:
            idx=next(iter(candidates)); target=records[idx]
            recipe_off+=int(any(k.startswith("off:") for k in target["source_keys"]))
            recipe_foodon+=int(not any(k.startswith("off:") for k in target["source_keys"]) and any(k.startswith("foodon:") for k in target["source_keys"]))
        elif norm in name_index:
            idx=name_index[norm]; target=records[idx]
            recipe_off+=int(any(k.startswith("off:") for k in target["source_keys"]))
            recipe_foodon+=int(any(k.startswith("foodon:") for k in target["source_keys"]))
        else:
            if len(candidates)>1: ambiguous+=len(occurrences)
            target=new_record(cleaned,"recipe:"+norm,[cleaned]); target["internal_key"]="recipe:"+norm
            idx=len(records); records.append(target); canonical_index[norm]=idx; name_index[norm]=idx; recipe_only+=1
        target["source_keys"].add("recipe:"+norm)
        for rid,i,ingredient,raw_cleaned in occurrences:
            arabic=str(ingredient.get("name") or "").strip()
            if arabic:
                if not target["names"]["ar"]: target["names"]["ar"].append(arabic)
                else: add_value(target["aliases"]["ar"],arabic)
            source_en=str(ingredient.get("sourceNameEn") or "").strip()
            if rid.startswith("focus:"):
                source_en=str(ingredient.get("sourceTextEn") or source_en).strip()
                if ":" in source_en: source_en=source_en.split(":",1)[0].strip()
            if source_en: add_value(target["aliases"]["en"],source_en)
            target["accepted_units"].add(normalize_unit(str(ingredient.get("unit") or "")))
            rows.append({"recipeId":rid,"ingredientIndex":i,"foodItemKey":target["internal_key"],
                         "sourceIngredientId":str(ingredient.get("id") or "") or None,"sourceName":source_en or raw_cleaned,
                         "matchedBy":"exact-alias" if target["internal_key"].startswith(("off:","foodon:")) else "recipe-canonical"})

    known_categories={c["id"] for c in CATEGORY_TREE}; name_owners={}
    for i,record in enumerate(records):
        norm=normalize_term(record["names"]["en"][0] if record["names"]["en"] else record["name_en"])
        if norm in name_owners and name_owners[norm]!=i: raise RuntimeError(f"Duplicate canonical food name: {record['name_en']!r}")
        name_owners[norm]=i
        if record["category_id"] not in known_categories: record["category_id"],record["subcategory_id"]="other-foods",None
        if record["subcategory_id"] and SUBCATEGORY_PARENT.get(record["subcategory_id"])!=record["category_id"]: record["subcategory_id"]=None
        if not record["accepted_units"]: record["accepted_units"].update(default_units(record["name_en"],record["category_id"]))
        record.setdefault("internal_key",sorted(record["source_keys"])[0])

    stable_ids=assign_stable_ids(records,registry)
    items=[clean_output_record(r,stable_ids[r["internal_key"]]) for r in records]
    items.sort(key=lambda x:(normalize_term(x["names"]["en"] or ""),x["id"]))
    id_by_key={r["internal_key"]:stable_ids[r["internal_key"]] for r in records}
    for row in rows: row["foodItemId"]=id_by_key[row.pop("foodItemKey")]
    rows.sort(key=lambda x:(x["recipeId"],x["ingredientIndex"]))
    ids=[x["id"] for x in items]; names=[normalize_term(x["names"]["en"] or "") for x in items]
    if len(ids)!=len(set(ids)): raise RuntimeError("Duplicate UUIDs in generated food catalogue.")
    if len(names)!=len(set(names)): raise RuntimeError("Duplicate canonical English names in generated food catalogue.")
    valid=set(ids)
    if any(r["foodItemId"] not in valid for r in rows): raise RuntimeError("Recipe mapping contains a dangling foodItemId.")
    expected=sum(len(r.get("ingredients") or []) for r in recipe_list)
    if len(rows)!=expected: raise RuntimeError(f"Recipe mapping incomplete: {len(rows)} of {expected} rows.")
    coverage={lang:sum(bool(x["names"][lang]) for x in items) for lang in ("ar","en","fr")}
    report={"schemaVersion":1,"sourceRecipeCount":len(recipe_list),"recipeIngredientRows":len(rows),
        "uniqueRecipeIngredientTerms":len(by_recipe_term),"foodItemCount":len(items),"offTaxonomyEntriesParsed":len(off_entries),
        "offUnclassifiedEntriesSkipped":off_excluded,"foodOnFoodProductClasses":len(product_classes),
        "foodOnOnlyItemsAdded":foodon_added,"foodOnClassesMatchedToExisting":foodon_matched,"foodOnAmbiguousMatchesSkipped":foodon_ambiguous,
        "recipeTermsMatchedToOff":recipe_off,"recipeTermsMatchedToFoodOnOnly":recipe_foodon,"recipeOnlyTermsAdded":recipe_only,
        "ambiguousRecipeRowsKeptSeparate":ambiguous,"curatedAlgerianItemsAdded":curated_added,"recipeRowsFullyMapped":len(rows),
        "translationCoverage":coverage,"imageUrlCount":sum(bool(x["imageUrl"]) for x in items),
        "countryOfOriginCount":sum(bool(x["countryOfOrigin"]) for x in items),"nutritionCount":sum(bool(x["nutrition"]) for x in items),
        "categoryCounts":dict(Counter(x["categoryId"] for x in items)),
        "matchingPolicy":"Exact normalized labels and aliases only; ambiguous aliases are never auto-merged."}
    return items,build_categories(),rows,report

def main() -> int:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--recipes",type=Path,default=DEFAULT_RECIPES)
    ap.add_argument("--output-dir",type=Path,default=DEFAULT_OUTPUT)
    ap.add_argument("--off-taxonomy",type=Path)
    ap.add_argument("--foodon-synonyms",type=Path)
    args=ap.parse_args()
    lock=json.loads(SOURCE_LOCK.read_text(encoding="utf-8")); off=lock["openFoodFactsIngredients"]; food=lock["foodOnSynonyms"]
    off_text=load_source_text(args.off_taxonomy,off["repository"],off["commit"],off["path"],off["gitBlobSha1"])
    food_text=load_source_text(args.foodon_synonyms,food["repository"],food["commit"],food["path"],food["gitBlobSha1"])
    recipes=json.loads(args.recipes.read_text(encoding="utf-8"))
    off_entries=parse_off_taxonomy(off_text); food_nodes,food_parents=parse_foodon_tsv(food_text)
    curated=json.loads(CURATED_FILE.read_text(encoding="utf-8")) if CURATED_FILE.exists() else []
    registry_path=args.output_dir/REGISTRY_NAME
    registry=json.loads(registry_path.read_text(encoding="utf-8")) if registry_path.exists() else {"version":1,"ids":{},"redirects":{}}
    items,categories,rows,report=build_catalogue(recipes,off_entries,food_nodes,food_parents,curated,registry)
    metadata={"schemaVersion":1,"idFormat":"UUID","catalogueType":"generic-food-items","marketProductsIncluded":False,
        "sources":{"openFoodFacts":{"repository":off["repository"],"commit":off["commit"],"path":off["path"],"license":off["license"],"attribution":off["attribution"]},
                   "foodOn":{"repository":food["repository"],"commit":food["commit"],"path":food["path"],"license":food["license"],"attribution":food["attribution"]}}}
    args.output_dir.mkdir(parents=True,exist_ok=True)
    outputs={"food-items.json":{**metadata,"items":items},"categories.json":{"schemaVersion":1,"categories":categories},
        "recipe-ingredient-map.json":{"schemaVersion":1,"rows":rows},"food-catalogue-report.json":report,REGISTRY_NAME:registry}
    for name,value in outputs.items():
        path=args.output_dir/name; tmp=path.with_suffix(path.suffix+".tmp")
        tmp.write_text(json.dumps(value,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8"); tmp.replace(path)
    print(json.dumps(report,ensure_ascii=False,indent=2,sort_keys=True)); return 0

if __name__=="__main__":
    try: raise SystemExit(main())
    except Exception as error:
        print(f"FOOD CATALOGUE BUILD FAILED: {error}",file=sys.stderr); raise SystemExit(1)
