# Hill Bros Rate Card — InDesign ExtendScript Toolkit

ExtendScript (`.jsx`) automation for building and maintaining the Hill Bros
rate card in Adobe InDesign.

## What's here

| File | Purpose |
| --- | --- |
| `scripts/RateCardBuilder.jsx` | Builds a complete, styled, print-ready rate card document from a CSV file (or embedded sample data). |
| `data/rates-sample.csv` | Sample rates file showing the expected columns. |

## Running the script

1. Open Adobe InDesign (CS6 or any CC version).
2. Go to **Window > Utilities > Scripts**.
3. Right-click the **User** folder → **Reveal in Explorer/Finder** and copy
   `RateCardBuilder.jsx` into that folder.
4. Double-click the script in the Scripts panel.
5. Choose your rates CSV when prompted — or press **Cancel** to build a demo
   document from the embedded sample data.

The script generates a new document with:

- A branded banner with company name and year
- One table per category (from the `Category` column) with a repeating
  header row, alternating row tints, and right-aligned formatted prices
- Automatic overflow onto extra pages with threaded text frames
- Page footers with issue date and "Page X of Y" numbering
- A single undo step (**Edit > Undo Build Rate Card** reverts everything)

### CSV format

The first row must be the header:

```csv
Category,Item,Description,Unit,Rate
Print Advertising,Full Page,"Full colour, run of paper",per insertion,4500
```

Quoted fields, embedded commas, and Windows/Unix line endings are handled.

### Customising

All the knobs are in the `CONFIG` object at the top of the script:
company name, tagline, currency prefix (`"K "`, `"$"`, …), page size
(A4/Letter), margins, fonts, and CMYK brand colours.

## Other scripts this toolkit can grow to include

Ideas for further InDesign automation — open an issue or ask for any of these:

- **Rate updater** — apply a percentage increase (or a new CSV) to prices in
  an *existing* laid-out document via GREP find/change, without rebuilding it.
- **Batch PDF exporter** — export print (PDF/X-4 with bleed) and web
  (low-res, RGB) PDFs of every open document in one click.
- **Data merge on steroids** — generate one personalised rate card per
  client from a spreadsheet (names, discount tiers, selected categories).
- **Style auditor / preflight** — flag overset text, missing fonts, local
  formatting overrides, and off-brand colours before handoff.
- **Image relinker** — repoint all links from a local folder to a shared
  drive (or vice versa) and relink lo-res to hi-res versions.
- **Price table importer** — refresh an existing tagged table in place from
  Excel/CSV while keeping all styling.
- **Versioned snapshots** — export IDML + PDF into dated archive folders on
  every save, for a simple change history.
