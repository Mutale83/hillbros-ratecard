/**********************************************************************
 * PreflightAuditor.jsx  —  Hill Bros Preflight / Style Auditor
 *
 * Audits the ACTIVE InDesign document before handoff and reports:
 *
 *   1. Overset text            — stories with hidden overflow text
 *   2. Fonts                   — missing, substituted or fauxed fonts
 *   3. Links                   — missing or out-of-date placed files
 *   4. Image resolution        — raster images below the effective-PPI
 *                                threshold (print safety)
 *   5. Image colour space      — RGB/Lab images in a CMYK print job
 *   6. Off-brand swatches      — swatches defined or used that are not
 *                                on the approved brand list
 *   7. Style hygiene           — paragraphs with local formatting
 *                                overrides, and text still on
 *                                [No Paragraph Style]/[Basic Paragraph]
 *   8. Pasteboard strays       — items sitting fully off the page
 *
 * Nothing in the document is modified — this script only reads.
 * Results appear in a scrollable window with a "Save Report…" button
 * that writes a plain-text report next to the document.
 *
 * HOW TO RUN
 *   Copy to the Scripts panel folder (Window > Utilities > Scripts,
 *   right-click "User" > Reveal), open the document to check, then
 *   double-click the script.
 *
 * Tune the thresholds and the approved swatch list in CONFIG below.
 **********************************************************************/

#target indesign

// ----------------------------- CONFIG ------------------------------
var CONFIG = {
    minEffectivePpi: 250,     // flag raster images below this (print: 250-300)
    flagRgbImages:   true,    // flag RGB/Lab placed images
    checkOverrides:  true,    // scan for local formatting overrides (slower)
    maxDetailLines:  40,      // per section, keep the report readable

    // Swatches that are allowed in the document. Built-ins
    // ([None], [Paper], [Black], [Registration]) are always allowed.
    approvedSwatches: [
        "RC Brand", "RC Ink", "RC Row Tint", "RC Rule"
    ],

    // Paragraph styles that count as "unstyled" text.
    unstyledNames: ["[No Paragraph Style]", "[Basic Paragraph]"]
};
// --------------------------------------------------------------------

main();

function main() {
    if (app.documents.length === 0) {
        alert("Open the document you want to audit, then run this script again.");
        return;
    }
    var doc = app.activeDocument;

    var sections = [];
    sections.push(checkOverset(doc));
    sections.push(checkFonts(doc));
    sections.push(checkLinks(doc));
    sections.push(checkImages(doc));
    sections.push(checkSwatches(doc));
    if (CONFIG.checkOverrides) { sections.push(checkStyleHygiene(doc)); }
    sections.push(checkPasteboard(doc));

    var report = buildReport(doc, sections);
    showReport(doc, report, countIssues(sections));
}

// ----------------------------- checks ------------------------------
// Each check returns { title: String, issues: [String] }.

function checkOverset(doc) {
    var issues = [], i, story, frames, where, snippet;
    for (i = 0; i < doc.stories.length; i++) {
        story = doc.stories[i];
        if (!story.overflows) { continue; }
        frames = story.textContainers;
        where = frames.length > 0 ? pageRef(frames[frames.length - 1]) : "?";
        snippet = snippetOf(story.contents);
        issues.push("Page " + where + ": overset story starting “" +
                    snippet + "”");
    }
    return { title: "Overset text", issues: issues };
}

function checkFonts(doc) {
    var issues = [], i, f, label;
    for (i = 0; i < doc.fonts.length; i++) {
        f = doc.fonts[i];
        label = null;
        if (f.status === FontStatus.NOT_AVAILABLE)      { label = "MISSING"; }
        else if (f.status === FontStatus.SUBSTITUTED)   { label = "SUBSTITUTED"; }
        else if (f.status === FontStatus.FAUXED)        { label = "FAUXED"; }
        if (label !== null) {
            issues.push(label + ": " + f.name.replace("\t", " "));
        }
    }
    return { title: "Fonts", issues: issues };
}

function checkLinks(doc) {
    var issues = [], i, lk;
    for (i = 0; i < doc.links.length; i++) {
        lk = doc.links[i];
        if (lk.status === LinkStatus.LINK_MISSING) {
            issues.push("MISSING: " + lk.name + "  (" + linkPage(lk) + ")");
        } else if (lk.status === LinkStatus.LINK_OUT_OF_DATE) {
            issues.push("OUT OF DATE: " + lk.name + "  (" + linkPage(lk) + ")");
        }
    }
    return { title: "Links", issues: issues };
}

function checkImages(doc) {
    var issues = [], gfx = doc.allGraphics, i, g, ppi, minPpi, space, name;
    for (i = 0; i < gfx.length; i++) {
        g = gfx[i];
        name = "(embedded)";
        try { if (g.itemLink.isValid) { name = g.itemLink.name; } } catch (e0) {}

        // Effective resolution — raster images only.
        try {
            ppi = g.effectivePpi;               // [x, y]
            minPpi = Math.min(ppi[0], ppi[1]);
            if (minPpi > 0 && minPpi < CONFIG.minEffectivePpi) {
                issues.push("LOW RES (" + minPpi + " ppi, min " +
                            CONFIG.minEffectivePpi + "): " + name +
                            "  (page " + pageRef(g) + ")");
            }
        } catch (e1) {}                          // vector/PDF: no PPI

        // Colour space.
        if (CONFIG.flagRgbImages) {
            try {
                space = String(g.space);
                if (space.indexOf("RGB") !== -1 || space.indexOf("Lab") !== -1) {
                    issues.push(space + " IMAGE: " + name +
                                "  (page " + pageRef(g) + ")");
                }
            } catch (e2) {}
        }
    }
    return { title: "Placed images", issues: issues };
}

function checkSwatches(doc) {
    var issues = [], allowed = {}, i, name;
    var builtins = ["None", "Paper", "Black", "Registration",
                    "[None]", "[Paper]", "[Black]", "[Registration]"];
    for (i = 0; i < builtins.length; i++) { allowed[builtins[i]] = true; }
    for (i = 0; i < CONFIG.approvedSwatches.length; i++) {
        allowed[CONFIG.approvedSwatches[i]] = true;
    }

    // Unapproved swatches defined in the document.
    var defined = {};
    for (i = 0; i < doc.swatches.length; i++) {
        name = doc.swatches[i].name;
        if (allowed[name] !== true) {
            defined[name] = true;
            issues.push("DEFINED off-brand swatch: " + name);
        }
    }

    // Unapproved colours actually applied to page-item fills/strokes.
    var items = doc.allPageItems, it, fill, stroke, seen = {};
    for (i = 0; i < items.length; i++) {
        it = items[i];
        fill = swatchName(it, "fillColor");
        stroke = swatchName(it, "strokeColor");
        if (fill !== null && allowed[fill] !== true && seen["f" + fill] !== true) {
            seen["f" + fill] = true;
            issues.push("USED as fill: " + fill + "  (first seen page " +
                        pageRef(it) + ")");
        }
        if (stroke !== null && allowed[stroke] !== true && seen["s" + stroke] !== true) {
            seen["s" + stroke] = true;
            issues.push("USED as stroke: " + stroke + "  (first seen page " +
                        pageRef(it) + ")");
        }
    }
    return { title: "Brand colours", issues: issues };
}

function checkStyleHygiene(doc) {
    var issues = [], i, j, story, flags, names, paras, snippet, unstyled = {};
    for (i = 0; i < CONFIG.unstyledNames.length; i++) {
        unstyled[CONFIG.unstyledNames[i]] = true;
    }
    for (i = 0; i < doc.stories.length; i++) {
        story = doc.stories[i];
        if (story.paragraphs.length === 0) { continue; }
        // One DOM round-trip per story instead of one per paragraph.
        flags = story.paragraphs.everyItem().styleOverridden;
        var styles = story.paragraphs.everyItem().appliedParagraphStyle;
        if (flags instanceof Array === false) { flags = [flags]; }
        if (styles instanceof Array === false) { styles = [styles]; }
        names = [];
        for (j = 0; j < styles.length; j++) { names.push(styles[j].name); }
        paras = null;
        for (j = 0; j < flags.length; j++) {
            if (flags[j] !== true && unstyled[names[j]] !== true) { continue; }
            if (paras === null) { paras = story.paragraphs; }
            snippet = snippetOf(paras[j].contents);
            if (flags[j] === true) {
                issues.push("OVERRIDE on “" + names[j] + "” (page " +
                            paraPage(paras[j]) + "): “" + snippet + "”");
            } else {
                issues.push("UNSTYLED text (" + names[j] + ", page " +
                            paraPage(paras[j]) + "): “" + snippet + "”");
            }
        }
    }
    return { title: "Style hygiene", issues: issues };
}

function checkPasteboard(doc) {
    var issues = [], i, j, items, it, kind;
    for (i = 0; i < doc.spreads.length; i++) {
        items = doc.spreads[i].pageItems;
        for (j = 0; j < items.length; j++) {
            it = items[j];
            try {
                if (it.parentPage === null) {
                    kind = it.constructor.name;
                    issues.push(kind + " on pasteboard of spread " + (i + 1) +
                                labelOf(it));
                }
            } catch (e) {}
        }
    }
    return { title: "Pasteboard strays", issues: issues };
}

// ----------------------------- report ------------------------------

function countIssues(sections) {
    var n = 0, i;
    for (i = 0; i < sections.length; i++) { n += sections[i].issues.length; }
    return n;
}

function buildReport(doc, sections) {
    var lines = [], i, j, sec, shown;
    var d = new Date();
    lines.push("PREFLIGHT REPORT — " + doc.name);
    lines.push("Generated " + d.toLocaleString());
    lines.push(repeat("=", 62));
    lines.push("");
    for (i = 0; i < sections.length; i++) {
        sec = sections[i];
        lines.push((sec.issues.length === 0 ? "[OK]  " : "[!!]  ") +
                   sec.title + "  (" + sec.issues.length + " issue" +
                   (sec.issues.length === 1 ? "" : "s") + ")");
        shown = Math.min(sec.issues.length, CONFIG.maxDetailLines);
        for (j = 0; j < shown; j++) {
            lines.push("      - " + sec.issues[j]);
        }
        if (sec.issues.length > shown) {
            lines.push("      … and " + (sec.issues.length - shown) + " more");
        }
        lines.push("");
    }
    var total = countIssues(sections);
    lines.push(repeat("=", 62));
    lines.push(total === 0
        ? "RESULT: ALL CLEAR — no issues found."
        : "RESULT: " + total + " issue" + (total === 1 ? "" : "s") +
          " to review before handoff.");
    return lines.join("\n");
}

function showReport(doc, report, total) {
    var w = new Window("dialog", "Preflight Auditor — " +
                       (total === 0 ? "All clear" : total + " issue(s)"));
    w.orientation = "column";
    w.alignChildren = "fill";

    var box = w.add("edittext", undefined, report,
                    { multiline: true, scrolling: true, readonly: true });
    box.preferredSize = [660, 440];

    var btns = w.add("group");
    btns.alignment = "right";
    var saveBtn = btns.add("button", undefined, "Save Report…");
    btns.add("button", undefined, "Close", { name: "ok" });

    saveBtn.onClick = function () {
        var base = doc.name.replace(/\.indd$/i, "");
        var folder = doc.saved ? doc.filePath : Folder.desktop;
        var f = new File(folder + "/" + base + "_preflight_" + stamp() + ".txt");
        f = f.saveDlg("Save preflight report");
        if (f === null) { return; }
        f.encoding = "UTF-8";
        if (f.open("w")) {
            f.write(report);
            f.close();
            alert("Report saved:\n" + f.fsName);
        } else {
            alert("Could not write the report file.");
        }
    };

    w.show();
}

// ----------------------------- helpers -----------------------------

function pageRef(item) {
    var p = null;
    try { p = item.parentPage; } catch (e1) {}
    if (p === null || p === undefined) {
        try { p = item.parentTextFrames[0].parentPage; } catch (e2) {}
    }
    return (p !== null && p !== undefined && p.isValid) ? p.name : "pasteboard";
}

function linkPage(lk) {
    try { return "page " + pageRef(lk.parent); } catch (e) { return "page ?"; }
}

function paraPage(para) {
    try {
        var frames = para.parentTextFrames;
        if (frames.length > 0) { return pageRef(frames[0]); }
    } catch (e) {}
    return "overset";
}

function labelOf(item) {
    try {
        var s = snippetOf(item.contents);
        if (s !== "") { return ": “" + s + "”"; }
    } catch (e) {}
    try {
        if (item.itemLink.isValid) { return ": " + item.itemLink.name; }
    } catch (e2) {}
    return "";
}

function snippetOf(contents) {
    var s = String(contents).replace(/[\r\n\t]+/g, " ").replace(/^\s+|\s+$/g, "");
    return s.length > 40 ? s.substring(0, 40) + "…" : s;
}

function swatchName(item, prop) {
    try {
        var c = item[prop];
        if (c !== undefined && c !== null && c.isValid) { return c.name; }
    } catch (e) {}
    return null;
}

function repeat(ch, n) {
    var s = "", i;
    for (i = 0; i < n; i++) { s += ch; }
    return s;
}

function stamp() {
    var d = new Date();
    function p(n) { return (n < 10 ? "0" : "") + n; }
    return d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + "-" +
           p(d.getHours()) + p(d.getMinutes());
}
