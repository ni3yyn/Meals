# Meals — Algeria household UX research notes

**Research type:** secondary research / desk review  
**Audience working hypothesis:** the person coordinating household meals and shopping in Algeria. This may be a wife, husband, parent, adult child, or another household member; the interface should not assume every household has the same roles or routine.

This is not a nationally representative user study. The evidence below is useful for product hypotheses, not proof that every Meals user behaves the same way. Validate the hypotheses with people using the product in different wilayas, household sizes, work routines, and levels of digital confidence.

## What the evidence suggests

### 1. Food budget visibility is a core job

The Algerian National Office of Statistics (ONS) reported that households spent **2,753.1 billion DZD on food in 2022**, representing **34.3% of total household expenditure**. The comparable share was 41.8% in 2011. Average annual food expenditure per household was reported as broadly stable between those two survey years, while per-person expenditure increased.

**Product implication:** the weekly budget should be a practical control, not a decorative dashboard number. Show the estimated purchase amount, missing-price count, and real spend separately. Avoid presenting estimates as exact savings or guaranteed store prices.

Source: [ONS — Household consumption expenditure, 2022 (food expenditure section)](https://www.ons.dz/IMG/pdf/Consom_menage2022.pdf)

### 2. Food inflation and actual price levels are different things

The ONS annual CPI note published in 2025 reports national food-goods price growth of **3.3% in 2024**, compared with **13.3% in 2023**. The ONS explicitly explains that CPI measures the change in prices over time; it does not give the current price level of a particular ingredient in a particular market.

**Product implication:** never turn an old observation or a national CPI number into a claim that an ingredient currently costs a specific amount in a user's wilaya. Keep demo values visibly marked, show the observation date and locality, and distinguish user-entered observations from verified sources.

Source: [ONS — Consumer Price Index 2015–2024, published July 2025](https://www.ons.dz/IMG/pdf/Note_annuelle_IPC2024.pdf)

### 3. Waste reduction needs to be made actionable

A 2025 study of **271 households in Chlef Province** found high awareness of household food-waste reduction, but a gap between awareness and day-to-day execution. It specifically identifies difficulties applying meal planning and food reuse.

**Product implication:** don't rely on tips or educational messages alone. Make the next practical action obvious: plan around what is already at home, keep the shopping list focused on what's missing, and make it easy to check off purchases.

Source: [Nacef Mohammed — The Reality of Household Food Waste Reduction Among Families in Chlef Province (2025)](https://asjp.cerist.dz/en/article/268131)

### 4. Shopping and storage routines are not uniform

A 2026 exploratory study of **100 married women in Constantine during Ramadan** reports that time constraints and convenience shape bread and bakery purchasing. In this sample, over-purchasing was a major reported factor in baguette waste; freezing leftovers was a common waste-reduction practice. The sample is small, regional, Ramadan-specific, and relatively well educated, so it should not be generalized to all Algerian women or all seasons.

**Product implication:** household planning should support flexible quantities and leftovers, not just generate a rigid seven-day menu. Future work should examine Ramadan routines, bread purchasing, freezer use, and how leftover food is reused.

Source: [Becila et al. — Bread and Bakery Products: Cultural Importance, Consumption, Purchase Patterns, and Household Waste During Ramadan in Constantine, Algeria (5 January 2026)](https://www.mdpi.com/2071-1050/18/1/543)

### 5. Older purchasing research suggests multiple retail contexts

A 2015 study of food-consumption patterns in three wilayas in western Algeria describes a mixture of traditional markets, neighborhood grocery shops, superettes, and larger supermarkets. The data is old and the sample is not national, so it is only a reminder not to design around supermarket-only shopping.

**Product implication:** shopping lists should work in a market, butcher, bakery, neighborhood shop, or supermarket. Familiar units and an easy-to-scan list matter more than store-specific integrations.

Source: [La Modernité du modèle de consommation alimentaire Méditerranéen : cas de l’Algérie (2015)](https://dspace.univ-tlemcen.dz/items/5f49c784-9777-4cb5-bfbf-25d1993f5d55)

## UX changes shipped from these findings

1. **Progressive onboarding:** the first screen asks only for weekly budget and household servings. A fast-start option is offered only after the user explicitly confirms there is no known food allergy. Other preferences remain available in the full setup and can be changed later. This reduces setup effort without silently assuming away a safety-critical allergy.
2. **Shopping while moving through a shop:** the list opens on remaining items. A separate “all items” control restores checked items for review. Progress still counts the full list.
3. **Human-readable quantities:** shopping and pantry quantities display grams as grams or kilograms, millilitres as millilitres or litres, and pieces as Arabic household terms. Internal calculations and stored recipe quantities remain in their original base units.
4. **Price honesty:** demo prices and unverified user-entered price notes remain clearly distinguished. The app must not call locally stored observations a verified or crowdsourced market price.

## Next research-led opportunities (not yet implemented)

- **Leftover-first planning:** let a person mark prepared food or bread as available, then offer safe, practical reuse suggestions before adding more purchases. This needs a clear model for cooked leftovers, portions, and dates; raw pantry quantities are not a substitute.
- **Ramadan mode:** allow suhoor / iftar planning and changed household routines without hard-coding Ramadan into the normal calendar.
- **Shopping list preferences:** support different purchasing habits (market, nearby grocery, butcher, bakery, supermarket) without assuming online delivery.
- **Seasonal and regional recipe coverage:** expand beyond a small sample of national recipes; collect dietary and regional feedback before deciding which dishes are “typical”.
- **Offline and low-effort use:** preserve core planning, pantry, and shopping flows on-device; make save states obvious and avoid requiring accounts, constant connectivity, or location access.

## Validation plan

Run short, moderated task-based sessions before adding more workflow complexity:

1. Recruit 8–12 household meal organizers across at least four wilayas, with variation in urban / rural context, household size, employment / caregiving routine, and digital comfort. Include people who shop at markets and neighborhood shops as well as supermarkets.
2. Ask participants to set up a realistic week, change one meal when plans change, add two items to the pantry, and use the shopping list during a simulated shop.
3. Observe completion without teaching the UI. Record time-to-first-plan, setup abandonment, errors in interpreting quantities, ability to find remaining items, and whether users trust the price estimate.
4. Ask which tasks happen weekly versus daily, who else contributes to meal decisions, what is bought by piece / bunch / weight, and how leftovers are stored or reused.
5. Treat this sample as formative usability research, not a statistically representative survey. Re-test after the next product iteration and compare task success.

## Product principles

- Help the household finish the next task; don't make it learn a complex planning system.
- Ask only for information needed now. Defer preferences that can be changed later.
- Never infer the absence of a food allergy. Ask explicitly before enabling the quick-start route.
- Separate estimated spend, recorded actual spend, and unknown prices.
- Prefer user-editable plans over automated plans that overwrite household decisions.
- Use Arabic-first language, keep French and dialect aliases searchable, and preserve a future LTR path.
- Design for the actual shop and kitchen, not an idealized supermarket or perfect inventory.
