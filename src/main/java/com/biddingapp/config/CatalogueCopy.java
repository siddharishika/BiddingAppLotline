package com.biddingapp.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogueCopy {

    private CatalogueCopy() {
    }

    static final String WATCH = """
            A steel-cased Omega Seamaster chronograph from 1968, still on its original tropical dial, with lugs that have never been polished flat. The papers are absent, but the movement was recently serviced and the watch runs as a working tool rather than a stopped display. It comes from a single private owner and has not been dressed up for a shop window. This lot is for someone who wants the watch as it was worn, including the honest marks of that wear.""";

    static final String BOOK = """
            Harper & Row’s 1970 first American edition of One Hundred Years of Solitude, still in its dust jacket. Wear is modest and sits mostly at the spine head, the kind that comes from a shelf rather than a suitcase. The interior is clean: no inscriptions, stamps, or underlining. Offered as a true reading copy of the right printing, not as a trophy to remain unopened under glass.""";

    static final String PAINTING = """
            An unsigned coastal study in oil on board, about twenty-four by eighteen inches, painted around 1920. The craquelure is consistent with age and has not been smothered in later overpaint. The gilt frame is later and can be changed without touching the board. It came from a New England estate: a spare, weather-heavy seascape that hung where the light actually reached the water.""";

    static final String CAMERA = """
            A Leica M3 body from 1955, double-stroke, with a working shutter and a clean viewfinder. The cosmetics show honest use — brassing and a few marks — and nothing has been buffed away to look newer than it is. No lens is included; this is the body alone. It is a camera that was used, not a replica bought to sit in a case.""";

    static final String VINYL = """
            A 1959 Columbia six-eye stereo pressing of Kind of Blue. The vinyl grades VG+; the jacket is VG, with the ring wear of a record that left the shelf. Labels are correct for the early stereo issue. This is a cornerstone jazz lot for someone who wants the record in the room, not a sealed trophy.""";

    static final String CHAIR = """
            A Danish teak lounge chair from the 1960s, the frame sculpted and unmarked as to maker. The wool seat has been newly upholstered, so the sitting surface is sound even where the wood shows its years. Joints are tight and the chair stands without a wobble. Offered as furniture to use, not as a labelled museum example.""";

    static final String DECK_CHAIR = """
            A folding beach chair in beech, still wearing its original red-and-cream canvas. The hardware is complete, including the fittings that usually go missing after a season of salt air. It came from a coastal house clearance, where it lived on the porch rather than in a display of “vintage leisure.” Sit in it; that is what it was for.""";

    static final String HAMPER = """
            A cream enamel picnic hamper with navy trim, the original cutlery straps still in place. Light wear shows on the lid, the sort that comes from being packed and unpacked, not from a single drop. It is a practical beach companion, not a kitchen prop. Offered with the collection of a house that actually went to the shore.""";

    static final String TIDE_CHART = """
            A hand-coloured coastal chart under glass, the print mid-century, the frame later and gilt. It hung in a porch that overlooked the water, so the fading is honest rather than gallery-even. You can still read the tides; that was the point of putting it on the wall. A picture, but also a working sheet from a house that watched the sea.""";

    static final String SHAKER = """
            A plated cocktail shaker from the 1930s, strainer cap intact, dents modest and old. It has the weight of a piece that lived behind a hotel bar rather than in a collector’s cabinet. The plating shows its age in the places hands actually held it. Offered as barware to use after midnight, not as an untouched specimen.""";

    static final String PHOTOGRAPH = """
            A gelatin silver print of a crowded dance floor, the photographer’s stamp on the verso and a later signature besides. The night is specific: smoke, dress, and a band you cannot hear. It is not a poster; it is a print that was kept, handled, and signed. Offered for the wall of someone who wants the room, not a generic “jazz” image.""";

    static final String LAMP = """
            A brass reading lamp with an adjustable arm, original switch, and a warm, even patina. The shade is later; the lamp itself is the sort meant for a desk rather than a drawing room. It still articulates smoothly and throws light where a reader needs it. From a quiet study, not from a lighting showroom.""";

    static final String DIARIES = """
            Five small morocco diaries from the 1920s, gilt edges, most pages unused. They read as a librarian’s unused stock from a quiet house, not as a cache of secrets. Bindings are sound and the gilt has not been recut. Offered as objects of a study, blank enough to remain themselves.""";

    static final String WEEKEND_BY_THE_SEA = """
            This collection gathers what a coastal house kept by the porch: a chair for the wind, a hamper for the sand, and a chart that told the tide. None of it was bought as a matching set. The pieces simply lived in the same rooms, used on the same weekends, and put away when the weather turned. They are offered together so a buyer can see the house as it was, while each lot keeps its own category and title.""";

    static final String AFTER_MIDNIGHT = """
            Two lots from the hours after the band packed up: a shaker that lived behind the bar, and a photograph of the floor while it was still full. They are not a theme park of “nightlife.” They are what remained when the club closed and someone took the tools and the picture home. Offered as one posting so the night stays in one place. Each lot is still its own object, with its own category and hammer.""";

    public static final String QUIET_STUDY = """
            A desk lamp and a set of unused morocco diaries from a room meant for reading rather than display. The lamp throws light where a page needs it; the diaries were stock, not a confession. Together they describe a quiet study without pretending to be a library sale. Each lot remains a separate object. The collection name is only for this posting.""";

    static final String KITCHEN_TABLE = """
            Two working pieces from a cook's dresser, offered as a short live collection: a copper sauté pan and a stoneware cream jug. They lived on the same table, not in a matched suite. Each lot keeps its own category and hammer.""";

    static final String PORCH_LIGHT = """
            Three lots still open on the floor together: a lantern, a folding table, and a woven seat. Bid each room on its own clock. They sat under the same porch light, used rather than displayed.""";

    static final String GUEST_BEDROOM = """
            Two pieces for a spare room that has not opened to the floor yet: a patchwork quilt and an ironstone ewer and basin. The issuer can still withdraw the posting. Each lot remains its own object.""";

    public static Map<String, String> lotsByTitle() {
        Map<String, String> copy = new LinkedHashMap<>();
        copy.put("1968 Omega Seamaster Chronograph", WATCH);
        copy.put("First edition of One Hundred Years of Solitude", BOOK);
        copy.put("Coastal study in oil, unsigned c. 1920", PAINTING);
        copy.put("Leica M3 body, 1955", CAMERA);
        copy.put("Kind of Blue — original six-eye Columbia pressing", VINYL);
        copy.put("Danish teak lounge chair, 1960s", CHAIR);
        copy.put("Canvas deck chair, striped", DECK_CHAIR);
        copy.put("Enamel picnic hamper", HAMPER);
        copy.put("Tide chart in a gilt frame", TIDE_CHART);
        copy.put("Silver cocktail shaker, 1930s", SHAKER);
        copy.put("Nightclub photograph, signed", PHOTOGRAPH);
        copy.put("Brass reading lamp", LAMP);
        copy.put("Set of morocco diaries, 1920s", DIARIES);
        return copy;
    }

    public static Map<String, String> collectionsByName() {
        Map<String, String> copy = new LinkedHashMap<>(lotsByTitle());
        copy.put("A weekend by the sea", WEEKEND_BY_THE_SEA);
        copy.put("After midnight at the club", AFTER_MIDNIGHT);
        copy.put("The quiet study", QUIET_STUDY);
        copy.put("On the kitchen table", KITCHEN_TABLE);
        copy.put("Under the porch light", PORCH_LIGHT);
        copy.put("Guest bedroom, not yet shown", GUEST_BEDROOM);
        return copy;
    }
}
