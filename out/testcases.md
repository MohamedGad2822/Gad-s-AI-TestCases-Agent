# Categories - test suite

Total: **10** cases | P1: 2 | P2: 5 | P3: 3 | Positive: 5 | Edge case: 3 | Negative: 1 | Validation: 1 | **TBD: 4**

> **Coverage notes:** The provided document (HG-P01-wireframes-v2.html) does not describe a standalone Categories management or CRUD module. In the wireframes and specifications, 'Category' exists strictly as a descriptive package classification attribute (e.g., 'Olympics 5-Star ⭐⭐⭐⭐⭐ Hotels', 'Olympics 4-Star ⭐⭐⭐⭐') displayed on package cards in Screen 1 (Select Package) and in the header summary of Screen 2 (Package Details). Category-based filtering controls on Screen 1 and backend category taxonomy configuration screens are not provided and require additional screenshots or specs.


## Package Card Category Display

### Categories | 1- Display 5-Star category with star symbols on package card (Positive)(P1)
`CAT-001`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the Select Package screen with search results populated.

**Steps:**
1. 1. Locate the package card for '5 Stars ✨ Galleria Hotel arrival 12 Oct'.
2. 2. Inspect the metadata lines beneath the hotel name.

**Expected result:** The metadata displays 'Category Olympics 5-Star ⭐⭐⭐⭐⭐'.

### Categories | 2- Display 4-Star category with star symbols on package card (Positive)(P2)
`CAT-002`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the Select Package screen with search results populated.

**Steps:**
1. 1. Locate the package card for '4 Stars ✨ Novotel Hotel arrival 12 Oct'.
2. 2. Inspect the metadata lines beneath the hotel name.

**Expected result:** The metadata displays 'Category Olympics 4-Star ⭐⭐⭐⭐'.

### Categories | 3- Verify category display on dimmed package card that cannot accommodate party (Positive)(P2)
`CAT-003`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the Select Package screen where party size exceeds a package's capacity.

**Steps:**
1. 1. Scroll to the dimmed card for '3 Stars ✨ ibis Hotel arrival 12 Oct'.
2. 2. Inspect the metadata lines in the package card body.

**Expected result - TBD:** TBD - confirm with dev whether dimmed unavailable package cards must display the Category line alongside Night count.

### Categories | 4- Render star rating emoji without character corruption or wrapping (Edge case)(P3)
`CAT-004`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is viewing package cards on the Select Package screen across desktop and mobile browsers.

**Steps:**
1. 1. Observe the Category line on all visible package cards.
2. 2. Verify the rendering of the star emojis (⭐⭐⭐⭐⭐).

**Expected result:** Star emojis render consistently across browsers without square fallback glyphs or breaking across multiple lines.

## Package Details Category Display

### Categories | 5- Retain selected package category in Package Details header (Positive)(P1)
`CAT-005`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the Select Package screen viewing the '5 Stars ✨ Galleria Hotel arrival 12 Oct' card with category 'Olympics 5-Star ⭐⭐⭐⭐⭐'.

**Steps:**
1. 1. Click the 'Select Package' button on the Galleria Hotel card.
2. 2. Inspect the header summary block on the Package Details screen.

**Expected result:** The header displays 'Category Olympics 5-Star ⭐⭐⭐⭐⭐' alongside Passengers and Dates.

### Categories | 6- Handle package with missing category attribute on Package Details (Negative)(P2)
`CAT-006`  -  evidence: assumption

**Preconditions:** A package record with a null or empty category attribute is loaded directly via URL.

**Steps:**
1. 1. Navigate to the Package Details screen for the package lacking category data.
2. 2. Inspect the header metadata block.

**Expected result - TBD:** TBD - confirm with dev whether the Category label is omitted gracefully or if a fallback default is displayed.

### Categories | 7- Display category on mobile viewport Package Details layout (Edge case)(P3)
`CAT-007`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User accesses Package Details on a mobile viewport (width <= 390px).

**Steps:**
1. 1. View the top summary block and the collapsed '▸ Hotel & inclusions' block.
2. 2. Tap to expand '▸ Hotel & inclusions'.

**Expected result - TBD:** TBD - confirm with dev whether Category is presented in the top summary or within the expanded Hotel & inclusions drawer on mobile.

## Category Filtering and Search

### Categories | 8- Verify category filter control availability on Select Package screen (Validation)(P2)
`CAT-008`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the Select Package screen with 14 packages listed.

**Steps:**
1. 1. Inspect the filter area and search summary bar above the package list.
2. 2. Check for category filter options (e.g., 5-Star, 4-Star, 3-Star).

**Expected result - TBD:** TBD - confirm with dev whether packages can be filtered by Category on Screen 1 matching Screen 3's filter pattern.

### Categories | 9- Maintain package category labels after modifying search criteria (Positive)(P2)
`CAT-009`  -  evidence: HG-P01-wireframes-v2.html

**Preconditions:** User is on the Select Package screen with categorized packages displayed.

**Steps:**
1. 1. Click '[ Modify Search ]' in the search summary bar.
2. 2. Change guest counts and apply search.
3. 3. Review the Category labels on the refreshed package cards.

**Expected result:** Each returned package maintains its correct Olympic star category classification.

### Categories | 10- Sanitize special characters or markup in Category API response (Edge case)(P3)
`CAT-010`  -  evidence: assumption

**Preconditions:** User initiates package search where API returns package data containing special characters or HTML tags in the category field.

**Steps:**
1. 1. Execute search on the Select Package screen.
2. 2. Inspect the rendered Category text in the package card and Package Details header.

**Expected result:** Category text is sanitized and rendered as plain text without breaking layout or executing script.
