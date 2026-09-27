# Categories - test suite

Total: **15** cases | P1: 4 | P2: 8 | P3: 3 | Positive: 11 | Validation: 1 | Edge case: 3 | **TBD: 2**

> **Coverage notes:** Wireframes show Category fields on package cards and package details ('Olympics 5-Star ⭐⭐⭐⭐⭐ Hotels', 'Olympics 4-Star ⭐⭐⭐⭐', and 3-Star), along with room categories ('Double Classic Room', 'Triple Classic Room', 'Family Suite') and filters for Room Type and Board Type. Category management screens (creation, editing, taxonomy hierarchy in admin) and dedicated search results category filters are not visible in the wireframe and require separate admin/search bar screenshots.


## Package Category Display - Select Package

### Categories | 1- Display 5-Star Olympics package category on package card (Positive)(P1)
`CAT-001`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the 'Select Package' screen with search results returned.

**Steps:**
1. Locate the package card for '5 Stars ✨ Galleria Hotel arrival 12 Oct'.
2. Inspect the metadata line under hotel information.

**Expected result:** The metadata line displays 'Category  Olympics 5-Star ⭐⭐⭐⭐⭐' alongside 'Night  6'.

### Categories | 2- Display 4-Star Olympics package category on package card (Positive)(P1)
`CAT-002`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the 'Select Package' screen with 14 packages loaded.

**Steps:**
1. Locate the package card for '4 Stars ✨ Novotel Hotel arrival 12 Oct'.
2. Inspect the metadata line below hotel name.

**Expected result:** The card displays 'Category  Olympics 4-Star ⭐⭐⭐⭐' alongside 'Night  6'.

### Categories | 3- Display category alignment between package title and category metadata (Validation)(P2)
`CAT-003`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the 'Select Package' screen viewing live packages.

**Steps:**
1. Read the title of the first card ('5 Stars ✨ Galleria Hotel arrival 12 Oct').
2. Compare the star rating in the title with the 'Category' line value.

**Expected result:** The category line displays 'Olympics 5-Star ⭐⭐⭐⭐⭐ Hotels' matching the 5-star title classification.

### Categories | 4- Category display on dimmed or unavailable package card (Positive)(P2)
`CAT-004`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User searches with party size of 5 guests and 2 rooms, returning incompatible packages.

**Steps:**
1. Locate the dimmed card for '3 Stars ✨ ibis Hotel arrival 12 Oct'.
2. Inspect the package title and hotel details block.

**Expected result:** The package card displays the 3-star rating classification in the title while showing capacity warning message.

### Categories | 5- Render star unicode icons in category label across different viewports (Edge case)(P3)
`CAT-005`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User accesses 'Select Package' on a mobile viewport (width <= 390px).

**Steps:**
1. Resize browser viewport to 390px.
2. Inspect the category rating stars on each visible package card.

**Expected result:** All five star icons ('⭐⭐⭐⭐⭐') render without clipping, wrapping mid-symbol, or encoding corruption.

### Categories | 6- Package record with missing category attribute (Edge case)(P3)
`CAT-006`  -  evidence: assumption

**Preconditions:** A package exists in the database with a null or empty category string.

**Steps:**
1. Search packages and locate the card with null category.
2. Observe the card metadata row where Category is normally rendered.

**Expected result - TBD:** TBD - confirm with dev whether the Category label is omitted entirely or falls back to a default unassigned state.

## Package Category Display - Package Details

### Categories | 7- Category persistence from package list to package details header (Positive)(P1)
`CAT-007`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on 'Select Package' screen viewing '5 Stars ✨ Galleria Hotel arrival 12 Oct'.

**Steps:**
1. Click 'Select Package' on the 5-Star Galleria Hotel package card.
2. Observe the metadata summary line in the Package Details header block.

**Expected result:** The header metadata displays 'Category Olympics 5-Star ⭐⭐⭐⭐⭐' alongside passenger count and travel dates.

### Categories | 8- Category persistence for 4-Star package details (Positive)(P2)
`CAT-008`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on 'Select Package' screen.

**Steps:**
1. Click 'Select Package' on '4 Stars ✨ Novotel Hotel arrival 12 Oct'.
2. Inspect the header metadata block on the loaded Package Details page.

**Expected result:** The header metadata reflects 'Category Olympics 4-Star ⭐⭐⭐⭐'.

## Room Categories - Package Details

### Categories | 9- Display distinct room category groupings in room selector (Positive)(P1)
`CAT-009`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on Package Details page for Galleria Hotel with room selector visible.

**Steps:**
1. Navigate to the 'Choose your rooms' section.
2. Verify each room category block heading.

**Expected result:** Three distinct room category sections are displayed: 'Double Classic Room', 'Triple Classic Room', and 'Family Suite'.

### Categories | 10- Display bed configuration tags for each room category (Positive)(P2)
`CAT-010`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is viewing the room combination component on Package Details.

**Steps:**
1. Inspect the bed chips under 'Double Classic Room'.
2. Inspect the bed chips under 'Triple Classic Room'.
3. Inspect the bed chips under 'Family Suite'.

**Expected result:** Double Classic displays 'Queen Bed', 'Twin Beds', 'King Bed'; Triple Classic displays 'Twin Beds', 'Sofa Bed'; Family Suite displays 'King Bed', 'Sofa Bed', 'Bunk Bed'.

### Categories | 11- Access room details modal or panel per room category (Positive)(P2)
`CAT-011`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is viewing room category headers in 'Choose your rooms'.

**Steps:**
1. Click 'See room details' on the 'Double Classic Room' category header.

**Expected result:** Detailed specification for Double Classic Room opens or expands in view.

### Categories | 12- Single room category package presentation (Edge case)(P3)
`CAT-012`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** A package is loaded that only contains one room category.

**Steps:**
1. Open a package that has only a single room type inventory.
2. Observe the 'Choose your rooms' section.

**Expected result:** A single room table is displayed with the stepper preset to the required count without an arrangement chooser.

## Category Filtering

### Categories | 13- Filter room inventory by Room Type category (Positive)(P2)
`CAT-013`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on Package Details with 'Filter: Board Type · Policy · Room Type' visible above room tables.

**Steps:**
1. Click on the 'Room Type' filter dropdown.
2. Select 'Family Suite' only.
3. Observe the room tables displayed.

**Expected result:** Only the 'Family Suite' room category table is displayed; 'Double Classic Room' and 'Triple Classic Room' are hidden.

### Categories | 14- Filter package search results by package star category (Positive)(P2)
`CAT-014`  -  evidence: assumption

**Preconditions:** User is on 'Select Package' screen with 14 packages displayed.

**Steps:**
1. Locate category filter control on the search results screen.
2. Select 'Olympics 5-Star Hotels'.

**Expected result - TBD:** TBD - confirm with dev whether Screen 1 provides a category filter bar to filter packages by 5-Star, 4-Star, or 3-Star.

### Categories | 15- Filter rooms by Board Type category (Positive)(P2)
`CAT-015`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is viewing room tables with Board Type filter visible.

**Steps:**
1. Click the 'Board Type' filter control.
2. Select 'Breakfast'.
3. Observe filtered rate rows.

**Expected result:** Only room rate rows offering 'Breakfast' are displayed across room categories.
