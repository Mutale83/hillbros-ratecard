/**********************************************************************
 * RateCardBuilder.jsx  —  Hill Bros Rate Card Generator for InDesign
 *
 * Builds a fully styled, print-ready rate card document from a CSV
 * file (or from the embedded sample data if you cancel the file
 * dialog). Rates are grouped into sections by the "Category" column,
 * each rendered as a table with a repeating header row, alternating
 * row fills and right-aligned prices. Content that doesn't fit is
 * flowed automatically onto extra pages.
 *
 * HOW TO RUN
 *   1. Open InDesign (CS6 or any CC version).
 *   2. Window > Utilities > Scripts, right-click "User" > Reveal…,
 *      copy this file there, then double-click it in the panel.
 *   3. Pick your CSV when prompted (see data/rates-sample.csv for
 *      the expected columns), or press Cancel to use sample data.
 *
 * CSV FORMAT (first row must be the header):
 *   Category,Item,Description,Unit,Rate
 *
 * Everything you are likely to want to tweak lives in CONFIG below.
 **********************************************************************/

#target indesign

// ----------------------------- CONFIG ------------------------------
var CONFIG = {
    companyName:    "HILL BROS",
    tagline:        "Official Rate Card " + (new Date()).getFullYear(),
    currencyPrefix: "K ",              // "K " Kwacha, "$", "ZMW ", "" for none
    pageSize:       "A4",              // "A4" or "LETTER"
    marginMM:       15,
    bodyFont:       "Arial",           // falls back silently if missing
    brand:          [0, 85, 100, 5],   // CMYK banner / header colour
    brandDark:      [0, 0, 0, 90],     // CMYK body text
    rowTint:        [0, 8, 12, 0],     // CMYK alternating row fill
    rule:           [0, 0, 0, 25],     // CMYK table rule colour
    askForCsv:      true               // false = always use SAMPLE_DATA
};

// Used when no CSV is chosen — also documents the expected columns.
var SAMPLE_DATA = [
    ["Category", "Item", "Description", "Unit", "Rate"],
    ["Print Advertising", "Full Page", "Full colour, run of paper", "per insertion", "4500"],
    ["Print Advertising", "Half Page", "Full colour, run of paper", "per insertion", "2600"],
    ["Print Advertising", "Quarter Page", "Full colour, run of paper", "per insertion", "1500"],
    ["Print Advertising", "Front Page Strip", "Full colour, premium position", "per insertion", "3200"],
    ["Digital", "Website Banner", "Leaderboard 728x90, all pages", "per week", "800"],
    ["Digital", "Social Media Post", "Sponsored post, all platforms", "per post", "350"],
    ["Digital", "Newsletter Feature", "Featured slot in weekly mailer", "per issue", "600"],
    ["Production Services", "Design & Artwork", "Ad design by in-house studio", "per hour", "250"],
    ["Production Services", "Photography", "On-location shoot, half day", "per session", "1200"],
    ["Production Services", "Copywriting", "Ad or advertorial copy", "per 300 words", "400"]
];

// --------------------------------------------------------------------

app.doScript(main, ScriptLanguage.JAVASCRIPT, [],
             UndoModes.ENTIRE_SCRIPT, "Build Rate Card");

function main() {
    var data = loadData();
    if (data === null) { return; }
    if (data.length < 2) {
        alert("The CSV needs a header row plus at least one data row.");
        return;
    }
    var doc = buildDocument(data);
    doc.windows.length && doc.windows[0].zoom(ZoomOptions.FIT_PAGE);
    alert("Rate card built: " + doc.pages.length + " page(s).\n" +
          "Review it, then export via File > Export (Adobe PDF).");
}

// ------------------------------ data -------------------------------

function loadData() {
    if (CONFIG.askForCsv) {
        var f = File.openDialog("Choose the rates CSV (Cancel = use sample data)", "*.csv");
        if (f !== null) {
            f.encoding = "UTF-8";
            if (!f.open("r")) {
                alert("Could not open file:\n" + f.fsName);
                return null;
            }
            var txt = f.read();
            f.close();
            var rows = parseCSV(txt);
            if (rows.length === 0) {
                alert("No rows found in the CSV.");
                return null;
            }
            return rows;
        }
    }
    return SAMPLE_DATA;
}

// Minimal CSV parser: handles quoted fields, embedded commas,
// doubled quotes and both \r\n and \n line endings.
function parseCSV(text) {
    var rows = [], row = [], field = "", inQuotes = false, i, c;
    for (i = 0; i < text.length; i++) {
        c = text.charAt(i);
        if (inQuotes) {
            if (c === '"') {
                if (text.charAt(i + 1) === '"') { field += '"'; i++; }
                else { inQuotes = false; }
            } else { field += c; }
        } else if (c === '"') {
            inQuotes = true;
        } else if (c === ",") {
            row.push(field); field = "";
        } else if (c === "\n" || c === "\r") {
            if (c === "\r" && text.charAt(i + 1) === "\n") { i++; }
            row.push(field); field = "";
            if (!(row.length === 1 && row[0] === "")) { rows.push(row); }
            row = [];
        } else {
            field += c;
        }
    }
    if (field !== "" || row.length > 0) {
        row.push(field);
        if (!(row.length === 1 && row[0] === "")) { rows.push(row); }
    }
    return rows;
}

// ---------------------------- document -----------------------------

function buildDocument(data) {
    var doc = app.documents.add();
    doc.viewPreferences.horizontalMeasurementUnits = MeasurementUnits.MILLIMETERS;
    doc.viewPreferences.verticalMeasurementUnits   = MeasurementUnits.MILLIMETERS;

    var dp = doc.documentPreferences;
    dp.facingPages = false;
    if (CONFIG.pageSize === "LETTER") { dp.pageWidth = "215.9mm"; dp.pageHeight = "279.4mm"; }
    else                              { dp.pageWidth = "210mm";   dp.pageHeight = "297mm";   }

    var m = CONFIG.marginMM;
    var mp = doc.pages[0].marginPreferences;
    mp.top = m; mp.bottom = m; mp.left = m; mp.right = m;

    var colors = {
        brand:     ensureColor(doc, "RC Brand",     CONFIG.brand),
        brandDark: ensureColor(doc, "RC Ink",       CONFIG.brandDark),
        rowTint:   ensureColor(doc, "RC Row Tint",  CONFIG.rowTint),
        rule:      ensureColor(doc, "RC Rule",      CONFIG.rule),
        paper:     doc.swatches.itemByName("Paper")
    };
    var styles = makeStyles(doc, colors);

    var pageW = parseFloat(dp.pageWidth);
    var pageH = parseFloat(dp.pageHeight);

    var bannerBottom = addBanner(doc.pages[0], pageW, colors, styles);

    // Main story frame, threaded onto new pages as it overflows.
    var frame = doc.pages[0].textFrames.add({
        geometricBounds: [bannerBottom + 8, m, pageH - m - 8, pageW - m]
    });

    var story = frame.parentStory;
    var sections = groupByCategory(data);
    for (var s = 0; s < sections.length; s++) {
        addSection(story, sections[s], styles, colors, pageW - 2 * m);
    }
    addFooterNote(story, styles);

    flowOverflow(doc, frame, pageW, pageH, m);
    addPageFooters(doc, pageW, pageH, m, styles);
    return doc;
}

function ensureColor(doc, name, cmyk) {
    var c = doc.colors.itemByName(name);
    if (!c.isValid) {
        c = doc.colors.add({
            name: name,
            model: ColorModel.PROCESS,
            space: ColorSpace.CMYK,
            colorValue: cmyk
        });
    }
    return c;
}

function safeFont(style, family, face) {
    try { style.appliedFont = family; } catch (e) {}
    try { style.fontStyle = face; } catch (e) {}
}

function makeStyles(doc, colors) {
    function para(name, props) {
        var st = doc.paragraphStyles.itemByName(name);
        if (!st.isValid) { st = doc.paragraphStyles.add({ name: name }); }
        st.properties = props;
        return st;
    }
    var s = {};

    s.company = para("RC Company", {
        pointSize: 26, leading: 28, fillColor: colors.paper,
        justification: Justification.LEFT_ALIGN, tracking: 40
    });
    safeFont(s.company, CONFIG.bodyFont, "Bold");

    s.tagline = para("RC Tagline", {
        pointSize: 11, leading: 14, fillColor: colors.paper,
        justification: Justification.LEFT_ALIGN, tracking: 60
    });
    safeFont(s.tagline, CONFIG.bodyFont, "Regular");

    s.section = para("RC Section Head", {
        pointSize: 13, leading: 16, fillColor: colors.brand,
        spaceBefore: 7, spaceAfter: 2.5, tracking: 20
    });
    safeFont(s.section, CONFIG.bodyFont, "Bold");

    s.tableHead = para("RC Table Head", {
        pointSize: 8.5, leading: 11, fillColor: colors.paper, tracking: 30
    });
    safeFont(s.tableHead, CONFIG.bodyFont, "Bold");

    s.cell = para("RC Cell", {
        pointSize: 9, leading: 12, fillColor: colors.brandDark
    });
    safeFont(s.cell, CONFIG.bodyFont, "Regular");

    s.cellRight = para("RC Cell Rate", {
        basedOn: s.cell, justification: Justification.RIGHT_ALIGN
    });
    safeFont(s.cellRight, CONFIG.bodyFont, "Bold");

    s.note = para("RC Note", {
        pointSize: 7.5, leading: 10, fillColor: colors.brandDark,
        spaceBefore: 6
    });
    safeFont(s.note, CONFIG.bodyFont, "Italic");

    s.footer = para("RC Footer", {
        pointSize: 7.5, leading: 9, fillColor: colors.brandDark,
        justification: Justification.CENTER_ALIGN, tracking: 30
    });
    safeFont(s.footer, CONFIG.bodyFont, "Regular");

    return s;
}

// Coloured banner with company name + tagline; returns its bottom edge (mm).
function addBanner(page, pageW, colors, styles) {
    var bannerH = 34;
    page.rectangles.add({
        geometricBounds: [0, 0, bannerH, pageW],
        fillColor: colors.brand,
        strokeWeight: 0
    });
    var tf = page.textFrames.add({
        geometricBounds: [8, CONFIG.marginMM, bannerH - 6, pageW - CONFIG.marginMM]
    });
    tf.contents = CONFIG.companyName + "\r" + CONFIG.tagline;
    tf.paragraphs[0].appliedParagraphStyle = styles.company;
    tf.paragraphs[1].appliedParagraphStyle = styles.tagline;
    return bannerH;
}

// [["Category","Item",...], ...rows] -> [{name, rows:[[Item,Desc,Unit,Rate],...]}]
function groupByCategory(data) {
    var sections = [], index = {}, i, row, cat, sec;
    for (i = 1; i < data.length; i++) {
        row = data[i];
        if (row.length < 5) { continue; }
        cat = trim(row[0]) || "General";
        if (index[cat] === undefined) {
            sec = { name: cat, rows: [] };
            index[cat] = sec;
            sections.push(sec);
        }
        index[cat].rows.push([trim(row[1]), trim(row[2]), trim(row[3]), trim(row[4])]);
    }
    return sections;
}

function addSection(story, section, styles, colors, tableW) {
    var ip = story.insertionPoints[-1];
    ip.contents = section.name + "\r";
    story.paragraphs[-1].appliedParagraphStyle = styles.section;

    var headers = ["ITEM", "DESCRIPTION", "UNIT", "RATE"];
    var table = story.insertionPoints[-1].tables.add({
        headerRowCount: 1,
        bodyRowCount: section.rows.length,
        columnCount: 4
    });

    var widths = [0.26, 0.40, 0.15, 0.19], c, r, cell, txt;
    for (c = 0; c < 4; c++) { table.columns[c].width = tableW * widths[c]; }

    for (c = 0; c < 4; c++) {
        cell = table.rows[0].cells[c];
        cell.contents = headers[c];
        cell.fillColor = colors.brand;
        cell.fillTint = 100;
        cell.texts[0].appliedParagraphStyle = styles.tableHead;
        if (c === 3) { cell.texts[0].justification = Justification.RIGHT_ALIGN; }
    }

    for (r = 0; r < section.rows.length; r++) {
        var tRow = table.rows[r + 1];
        if (r % 2 === 1) {
            tRow.cells.everyItem().fillColor = colors.rowTint;
            tRow.cells.everyItem().fillTint = 100;
        }
        for (c = 0; c < 4; c++) {
            cell = tRow.cells[c];
            txt = section.rows[r][c];
            cell.contents = (c === 3) ? fmtMoney(txt) : txt;
            cell.texts[0].appliedParagraphStyle =
                (c === 3) ? styles.cellRight : styles.cell;
        }
    }

    // Clean rules: no verticals, thin horizontal rules only.
    var cells = table.cells.everyItem();
    cells.properties = {
        topEdgeStrokeWeight: 0,
        leftEdgeStrokeWeight: 0,
        rightEdgeStrokeWeight: 0,
        bottomEdgeStrokeWeight: 0.5,
        bottomEdgeStrokeColor: colors.rule,
        topInset: 1.2, bottomInset: 1.2, leftInset: 1.5, rightInset: 1.5
    };

    story.insertionPoints[-1].contents = "\r";
}

function addFooterNote(story, styles) {
    story.insertionPoints[-1].contents =
        "All rates are exclusive of VAT and subject to change without notice. " +
        "Agency commission and volume discounts available on request.\r";
    story.paragraphs[-1].appliedParagraphStyle = styles.note;
}

// Thread new frames onto new pages while the story overflows.
function flowOverflow(doc, frame, pageW, pageH, m) {
    var guard = 0;
    while (frame.overflows && guard < 50) {
        var page = doc.pages.add();
        var next = page.textFrames.add({
            geometricBounds: [m, m, pageH - m - 8, pageW - m]
        });
        frame.nextTextFrame = next;
        frame = next;
        guard++;
    }
}

function addPageFooters(doc, pageW, pageH, m, styles) {
    var d = new Date();
    var months = ["January","February","March","April","May","June","July",
                  "August","September","October","November","December"];
    var stamp = CONFIG.companyName + "  |  Issued " +
                d.getDate() + " " + months[d.getMonth()] + " " + d.getFullYear();
    for (var p = 0; p < doc.pages.length; p++) {
        var tf = doc.pages[p].textFrames.add({
            geometricBounds: [pageH - m - 5, m, pageH - m + 1, pageW - m]
        });
        tf.contents = stamp + "  |  Page " + (p + 1) + " of #TOTAL#";
        tf.paragraphs[0].appliedParagraphStyle = styles.footer;
    }
    // Fill in the total now that all pages exist.
    for (p = 0; p < doc.pages.length; p++) {
        var frames = doc.pages[p].textFrames;
        for (var f = 0; f < frames.length; f++) {
            if (frames[f].contents.toString().indexOf("#TOTAL#") !== -1) {
                app.findGrepPreferences = NothingEnum.NOTHING;
                app.changeGrepPreferences = NothingEnum.NOTHING;
                app.findGrepPreferences.findWhat = "#TOTAL#";
                app.changeGrepPreferences.changeTo = String(doc.pages.length);
                frames[f].changeGrep();
                app.findGrepPreferences = NothingEnum.NOTHING;
                app.changeGrepPreferences = NothingEnum.NOTHING;
            }
        }
    }
}

// ----------------------------- helpers -----------------------------

function trim(s) {
    return String(s === undefined ? "" : s).replace(/^\s+|\s+$/g, "");
}

// "4500" -> "K 4,500.00"; non-numeric values pass through unchanged.
function fmtMoney(raw) {
    var n = parseFloat(String(raw).replace(/[^0-9.\-]/g, ""));
    if (isNaN(n)) { return raw; }
    var neg = n < 0;
    n = Math.abs(n);
    var whole = String(Math.floor(n));
    var cents = Math.round((n - Math.floor(n)) * 100);
    var centsStr = (cents < 10 ? "0" : "") + cents;
    var out = "", count = 0, i;
    for (i = whole.length - 1; i >= 0; i--) {
        out = whole.charAt(i) + out;
        count++;
        if (count % 3 === 0 && i > 0) { out = "," + out; }
    }
    return (neg ? "-" : "") + CONFIG.currencyPrefix + out + "." + centsStr;
}
