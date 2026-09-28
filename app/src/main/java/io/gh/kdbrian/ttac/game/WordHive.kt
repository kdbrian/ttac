package io.gh.kdbrian.ttac.game

import kotlin.math.abs
import kotlin.random.Random

/** Axial hex coordinate. */
data class Hex(val q: Int, val r: Int) {
    val s: Int get() = -q - r
    operator fun plus(o: Hex) = Hex(q + o.q, r + o.r)
    fun distanceTo(o: Hex): Int = maxOf(abs(q - o.q), abs(r - o.r), abs(s - o.s))

    companion object {
        /** The six straight directions through a hex grid. */
        val DIRECTIONS = listOf(Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1))

        fun disc(radius: Int): List<Hex> = buildList {
            for (q in -radius..radius) for (r in maxOf(-radius, -q - radius)..minOf(radius, -q + radius)) add(Hex(q, r))
        }

        /** Cells on the straight line from [a] to [b], or null if they don't share an axis. */
        fun line(a: Hex, b: Hex): List<Hex>? {
            if (a == b) return listOf(a)
            val dq = b.q - a.q
            val dr = b.r - a.r
            val ds = b.s - a.s
            if (dq != 0 && dr != 0 && ds != 0) return null
            val n = maxOf(abs(dq), abs(dr), abs(ds))
            return (0..n).map { k -> Hex(a.q + dq / n * k, a.r + dr / n * k) }
        }
    }
}

data class PlacedWord(val word: String, val cells: List<Hex>)

/** How big and wordy each level is. */
data class HiveSpec(val level: Int) {
    val radius: Int = when {
        level <= 2 -> 2
        level <= 5 -> 3
        else -> 4
    }
    val wordCount: Int = minOf(3 + level, when (radius) { 2 -> 4; 3 -> 7; else -> 11 })
    val minLength: Int = if (level <= 3) 3 else 4
    val maxLength: Int = minOf(8, 4 + level / 2)
}

/** What a traced sequence of cells turned out to be. */
sealed interface HiveVerdict {
    data class Target(val word: PlacedWord) : HiveVerdict
    data class Extra(val word: String) : HiveVerdict
    data class Repeat(val word: String) : HiveVerdict
    data class Rejected(val word: String) : HiveVerdict
}

/**
 * A hive of letters hiding [words] as paths of neighbouring cells. Players trace any path of
 * adjacent cells; target words are accepted, other real words count as extras.
 */
class HivePuzzle(val spec: HiveSpec, val letters: Map<Hex, Char>, val words: List<PlacedWord>) {
    val cells: List<Hex> get() = letters.keys.toList()

    fun spell(path: List<Hex>): String = path.mapNotNull { letters[it] }.joinToString("")

    fun evaluate(path: List<Hex>, found: Set<String>, extras: Set<String>): HiveVerdict {
        val word = spell(path)
        if (word.length < MIN_WORD) return HiveVerdict.Rejected(word)
        if (word in found || word in extras) return HiveVerdict.Repeat(word)
        // Any path that spells a target counts, not just the one we hid.
        words.firstOrNull { it.word == word }?.let { return HiveVerdict.Target(it) }
        if (word in WordBank.dictionary) return HiveVerdict.Extra(word)
        return HiveVerdict.Rejected(word)
    }

    /** Rearranges the whole hive; every word not yet [found] is re-hidden so it stays spellable. */
    fun shuffled(found: Set<String>, random: Random = Random.Default): HivePuzzle {
        val remaining = words.filter { it.word !in found }.map { it.word }
        val fresh = layout(spec, remaining, random)
        val byWord = fresh.second.associateBy { it.word }
        return HivePuzzle(spec, fresh.first, words.map { byWord[it.word] ?: PlacedWord(it.word, emptyList()) })
    }

    companion object {
        const val MIN_WORD = 3
        private const val FREQUENT = "EEEEEEAAAAARRRRIIIIOOOOTTTTNNNNSSSLLLCCUUDDPPMMHHGGBBFYWKV"

        fun adjacent(a: Hex, b: Hex): Boolean = a.distanceTo(b) == 1

        fun generate(spec: HiveSpec, random: Random = Random.Default): HivePuzzle {
            val pool = WordBank.words.filter { it.length in spec.minLength..spec.maxLength }.shuffled(random)
            val (letters, placed) = layout(spec, pool, random, limit = spec.wordCount)
            return HivePuzzle(spec, letters, placed)
        }

        /** Hides up to [limit] of [candidates] as snaking paths, then fills the gaps. */
        private fun layout(spec: HiveSpec, candidates: List<String>, random: Random, limit: Int = candidates.size): Pair<Map<Hex, Char>, List<PlacedWord>> {
            val grid = Hex.disc(spec.radius)
            val gridSet = grid.toSet()
            val letters = HashMap<Hex, Char>()
            val placed = ArrayList<PlacedWord>()
            for (word in candidates) {
                if (placed.size >= limit) break
                val path = snake(word, grid, gridSet, letters, random) ?: continue
                path.forEachIndexed { i, h -> letters[h] = word[i] }
                placed += PlacedWord(word, path)
            }
            for (h in grid) if (h !in letters) letters[h] = FREQUENT[random.nextInt(FREQUENT.length)]
            return letters to placed
        }

        /** Random depth-first walk through free (or matching) neighbours that spells [word]. */
        private fun snake(word: String, grid: List<Hex>, gridSet: Set<Hex>, letters: Map<Hex, Char>, random: Random): List<Hex>? {
            var budget = 4000
            fun walk(path: MutableList<Hex>): Boolean {
                if (path.size == word.length) return path.any { letters[it] == null }
                if (--budget <= 0) return false
                for (d in Hex.DIRECTIONS.shuffled(random)) {
                    val next = path.last() + d
                    if (next !in gridSet || next in path) continue
                    val existing = letters[next]
                    if (existing != null && existing != word[path.size]) continue
                    path += next
                    if (walk(path)) return true
                    path.removeAt(path.lastIndex)
                }
                return false
            }
            for (start in grid.shuffled(random)) {
                val existing = letters[start]
                if (existing != null && existing != word[0]) continue
                val path = mutableListOf(start)
                if (walk(path)) return path
                if (budget <= 0) return null
            }
            return null
        }
    }
}

/** A scored word: base points plus any long-word bonus, and its bonus tier name. */
data class HiveScore(val base: Int, val bonus: Int, val tier: String?) {
    val total: Int get() = base + bonus
}

/**
 * Longer words pay more per letter and earn escalating bonuses:
 * 5 letters "Nice", 6 "Great", 7 "Superb", 8+ "Legendary". Extras pay less; hints halve targets.
 */
fun hiveScore(word: String, level: Int, extra: Boolean, hinted: Boolean): HiveScore {
    val len = word.length
    var base = len * (if (extra) 4 else 10) * level
    if (hinted) base /= 2
    val (bonus, tier) = when {
        len >= 8 -> 150 to "Legendary"
        len == 7 -> 90 to "Superb"
        len == 6 -> 50 to "Great"
        len == 5 -> 20 to "Nice"
        else -> 0 to null
    }
    val scaled = bonus * level / (if (extra) 2 else 1)
    return HiveScore(base, scaled, tier)
}

fun hiveHintCost(level: Int): Int = 15 + 10 * level

object WordBank {
    val words: List<String> = """
        BEE HIVE HONEY WAX BUZZ SUN SKY SEA OAK ELM ASH FOX OWL CAT DOG EMU YAK ANT BAT COD EEL
        GEM ORE OAR TEA JAM PIE BUN RYE FIG KIWI LIME PEAR PLUM DATE CORN RICE BEAN MINT SAGE
        STAR MOON NOVA COMET ORBIT SPACE PLANET ROCKET GALAXY NEBULA
        TREE LEAF ROOT SEED BLOOM PETAL FLOWER GARDEN FOREST MEADOW MAPLE CEDAR WILLOW
        RAIN SNOW HAIL STORM CLOUD THUNDER BREEZE FROST MIST WIND
        RIVER LAKE POND OCEAN WAVE TIDE CORAL REEF SHORE ISLAND HARBOR
        FIRE FLAME BLAZE EMBER SPARK HEAT GLOW LAVA MAGMA
        GAME PLAY WIN SCORE LEVEL BONUS QUEST MEDAL CROWN TROPHY PUZZLE STREAK
        BLOCK SHAPE GRID LINE CELL CUBE PRISM CIRCLE SQUARE HEXAGON
        MUSIC SONG BEAT DRUM FLUTE PIANO GUITAR VIOLIN RHYTHM MELODY
        APPLE MANGO LEMON CHERRY BERRY PEACH GRAPE MELON BANANA ORANGE
        TIGER LION BEAR WOLF DEER HORSE ZEBRA PANDA KOALA OTTER EAGLE RAVEN FALCON PARROT
        BREAD CHEESE PASTA SUGAR SPICE SALT BUTTER CREAM COCOA
        HOUSE HOME DOOR ROOF WALL ROOM TOWER CASTLE BRIDGE CABIN
        LIGHT DARK SHADOW COLOR AMBER GOLD SILVER COPPER IRON STEEL
        SMILE LAUGH DREAM HAPPY BRAVE CALM KIND WISE BOLD SWIFT
        TRAIN PLANE SHIP BOAT BIKE TRUCK WAGON SAIL
        CODE BYTE DATA PIXEL SCREEN ROBOT LASER
        PAPER PENCIL BOOK STORY WORD LETTER POEM
    """.trim().split(Regex("\\s+")).map { it.uppercase() }.distinct()

    /** Common words accepted as extras when traced (targets are always valid too). */
    private val common: List<String> = """
        ACE ACT ADD AGE AGO AID AIM AIR ALE ALL AND ANY APE ARC ARE ARK ARM ART ATE AWE AXE BAD BAG BAN BAR
        BED BEG BET BID BIG BIN BIT BOW BOX BOY BUD BUG BUS BUT BUY CAB CAN CAP CAR COT COW CRY CUB CUP CUT
        DAD DAM DAY DEN DEW DID DIE DIG DIM DIP DOE DOT DRY DUE DUG DYE EAR EAT EGG EGO END ERA EVE EYE FAN
        FAR FAT FED FEE FEW FIN FIT FLY FOE FOG FOR FUN FUR GAP GAS GEL GET GOT GUM GUN GUT GUY HAD HAM HAS
        HAT HEN HER HID HIM HIP HIS HIT HOG HOP HOT HOW HUB HUE HUG HUT ICE ICY ILL INK INN ION IRE ITS IVY
        JAR JAW JET JOB JOG JOY KEY KID KIN KIT LAB LAD LAG LAP LAW LAY LED LEG LET LID LIE LIP LIT LOG LOT
        LOW MAD MAN MAP MAT MAY MEN MET MID MIX MOB MOP MUD MUG NAP NET NEW NIL NOD NOR NOT NOW NUT OAT ODD
        OFF OIL OLD ONE OPT ORB OUR OUT OWE OWN PAD PAN PAT PAW PAY PEA PEG PEN PET PIG PIN PIT POD POT PRO
        PUB PUN PUP PUT RAG RAM RAN RAP RAT RAW RED REP RIB RID RIG RIM RIP ROB ROD ROT ROW RUB RUG RUN SAD
        SAT SAW SAY SET SEW SHE SHY SIN SIP SIR SIT SIX SOB SOD SON SOW SOY SPA SPY SUB SUM TAB TAG TAN TAP
        TAR TEN THE TIE TIN TIP TOE TON TOO TOP TOY TRY TUB TUG TWO USE VAN VAT VET VIA VOW WAR WAS WAY WEB
        WED WET WHO WHY WIG WIT WOE WON YES YET YOU ZAP ZEN ZIP ZOO
        ABLE ACHE ACID AREA ARMY AUNT BABY BACK BAKE BALL BAND BANK BARN BASE BATH BEAM BELL BELT BEND BEST
        BIRD BITE BLUE BOAT BODY BONE BOOT BORN BOTH BOWL BURN BUSY CAGE CAKE CALL CAME CAMP CARD CARE CART
        CASE CASH CAST CAVE CHAT CHEF CHIN CHIP CITY CLAP CLAY CLIP CLUB COAL COAT COIN COLD COOK COOL COPE
        COST CREW CROP CURE CUTE DARE DEAL DEAR DEEP DESK DIAL DICE DIET DIRT DISH DIVE DOLL DOME DONE DOSE
        DOVE DOWN DRAW DROP DUCK DUNE DUST DUTY EACH EARN EASE EAST EASY EDGE ELSE EVEN EVER EXIT FACE FACT
        FAIR FALL FAME FARM FAST FATE FEAR FEED FEEL FEET FELT FILE FILL FILM FIND FINE FIRM FISH FIST FLAG
        FLAT FLEW FLIP FLOW FOAM FOLD FOLK FOOD FOOT FORK FORM FORT FREE FROG FROM FUEL FULL FUSE GAIN GATE
        GAVE GEAR GIFT GIRL GIVE GLAD GLUE GOAL GOAT GOES GONE GOOD GRAB GRAY GREW GRIN GROW GULF HAIR HALF
        HALL HAND HANG HARD HARM HAVE HEAD HEAL HEAR HELD HELP HERB HERD HERO HIDE HIGH HIKE HILL HINT HOLD
        HOLE HOOK HOPE HORN HOST HOUR HUGE HUNT HURT IDEA INCH INTO IRON ITEM JAZZ JOIN JOKE JUMP JUST KEEN
        KEEP KICK KING KISS KITE KNEE KNOT KNOW LACE LADY LAID LAMP LAND LANE LAST LATE LAZY LEAD LEAN LEFT
        LEND LENS LESS LIFE LIFT LIKE LINK LION LIST LIVE LOAD LOAN LOCK LONG LOOK LOOP LORD LOSE LOSS LOST
        LOUD LOVE LUCK MADE MAIL MAIN MAKE MALL MANY MARK MASK MATH MEAL MEAN MEAT MEET MELT MENU MESS MILD
        MILE MILK MILL MIND MINE MISS MODE MOOD MORE MOST MOVE MUCH MUST NAME NAVY NEAR NEAT NECK NEED NEST
        NEWS NEXT NICE NINE NONE NOON NOSE NOTE OPEN OVAL OVEN OVER PACE PACK PAGE PAID PAIN PAIR PALM PARK
        PART PASS PAST PATH PEAK PICK PILE PINE PINK PIPE PLAN PLOT PLUG PLUS POLE POOL POOR PORT POSE POST
        POUR PRAY PULL PUMP PURE PUSH RACE RAFT RAGE RAIL RANK RARE RATE READ REAL REST RICH RIDE RING RISE
        RISK ROAD ROAR ROBE ROLE ROLL ROPE ROSE RUDE RULE RUSH SAFE SAID SALE SAME SAND SAVE SEAL SEAT SEEK
        SEEM SEEN SELF SELL SEND SHED SHIP SHOE SHOP SHOT SHOW SHUT SICK SIDE SIGN SILK SING SINK SITE SIZE
        SKIN SLID SLIM SLIP SLOW SNAP SOAP SOCK SOFT SOIL SOLD SOLE SOME SONG SOON SORT SOUL SOUP SOUR SPIN
        SPOT STAY STEM STEP STIR STOP SUCH SUIT SURE SWIM TAIL TAKE TALE TALK TALL TAME TANK TAPE TASK TEAM
        TEAR TELL TEND TENT TERM TEST TEXT THAN THAT THEM THEN THEY THIN THIS TIDE TIDY TILE TIME TINY TIRE
        TOLD TONE TOOK TOOL TOUR TOWN TRAP TRIM TRIP TRUE TUBE TUNE TURN TWIN TYPE UNIT UPON USED USER VASE
        VAST VERY VIEW VOTE WAGE WAIT WAKE WALK WANT WARM WARN WASH WAVE WEAK WEAR WEEK WELL WENT WERE WEST
        WHAT WHEN WHOM WIDE WIFE WILD WILL WINE WING WIRE WISH WITH WOKE WOLF WOOD WOOL WORE WORK WORM WRAP
        YARD YEAR YELL YOUR ZERO ZONE
        ABOUT ABOVE ACTOR ADULT AFTER AGAIN AGENT ALARM ALBUM ALERT ALIKE ALIVE ALLOW ALONE ALONG ANGEL ANGER
        ANGLE ANGRY ANKLE ARENA ARGUE ARISE ARROW ASIDE AWAKE AWARD AWARE BADGE BASIC BEACH BEAST BEGIN BEING
        BELOW BENCH BIRTH BLACK BLADE BLAME BLANK BLAST BLEND BLIND BLOOD BOARD BOOST BRAIN BRAND BRASS BRICK
        BRIEF BRING BROAD BROWN BRUSH BUILD BUNCH CABLE CANDY CARGO CARRY CATCH CAUSE CHAIN CHAIR CHALK CHARM
        CHART CHASE CHEAP CHECK CHEST CHIEF CHILD CHILL CLAIM CLASS CLEAN CLEAR CLIMB CLOCK CLOSE COACH COAST
        COUCH COUNT COURT COVER CRAFT CRANE CRASH CRAZY CREEK CRISP CROSS CROWD CRUSH CURVE CYCLE DAILY DANCE
        DELAY DEPTH DIARY DIRTY DOUBT DOZEN DRAFT DRAIN DRAMA DRANK DRESS DRIFT DRILL DRINK DRIVE EAGER EARLY
        EARTH EIGHT ELBOW EMPTY ENJOY ENTER EQUAL ERROR EVENT EVERY EXACT EXTRA FAINT FAITH FALSE FANCY FEAST
        FENCE FIELD FIFTY FIGHT FINAL FIRST FLASH FLOAT FLOOD FLOOR FLOUR FOCUS FORCE FRAME FRESH FRONT FRUIT
        FUNNY GHOST GIANT GLASS GLOBE GLOVE GRACE GRADE GRAIN GRAND GRASS GREAT GREEN GREET GROUP GUARD GUESS
        GUEST GUIDE HABIT HEART HEAVY HELLO HOBBY HONOR HORSE HOTEL HUMAN HUMOR HURRY IMAGE INDEX INNER INPUT
        JEANS JELLY JEWEL JOINT JUDGE JUICE KNIFE KNOCK LABEL LARGE LATER LAYER LEARN LEMON LEVEL LIMIT LOCAL
        LOGIC LOOSE LUCKY LUNCH MAGIC MAJOR MAKER MARCH MATCH MAYBE MAYOR METAL MIGHT MINOR MIXED MODEL MONEY
        MONTH MOTOR MOUNT MOUSE MOUTH MOVIE MUDDY NERVE NEVER NIGHT NOISE NORTH NOVEL NURSE OCCUR OFFER OFTEN
        OLIVE ONION OPERA ORDER OTHER OUTER OWNER PAINT PANEL PARTY PATCH PAUSE PEACE PEARL PHONE PHOTO PIANO
        PIECE PILOT PITCH PLACE PLAIN PLANT PLATE POINT POUND POWER PRESS PRICE PRIDE PRIME PRIZE PROOF PROUD
        PUPIL PURSE QUEEN QUICK QUIET QUITE RADIO RAISE RANGE RAPID REACH READY RELAX REPLY RIDER RIDGE RIGHT
        RIVAL ROAST ROBIN ROUGH ROUND ROUTE ROYAL RULER SALAD SAUCE SCALE SCARF SCENE SCOUT SENSE SERVE SEVEN
        SHADE SHAKE SHARE SHARP SHEEP SHEET SHELF SHELL SHIFT SHINE SHIRT SHOCK SHOOT SHORT SHOUT SIGHT SKILL
        SLEEP SLICE SLIDE SMALL SMART SMELL SMOKE SNACK SNAKE SOLID SOLVE SOUND SOUTH SPARE SPEAK SPEED SPELL
        SPEND SPINE SPOON SPORT SPRAY STAFF STAGE STAIR STAMP STAND STEAM STICK STILL STONE STOOL STORE STOVE
        STRAW STRIP STUDY STYLE SUNNY SUPER SWEET SWING TABLE TASTE TEACH THEME THICK THING THINK THREE THROW
        THUMB TIRED TITLE TOAST TODAY TOOTH TOPIC TORCH TOTAL TOUCH TOUGH TOWEL TRACK TRADE TRAIL TREAT TREND
        TRIAL TRICK TRUST TRUTH TWICE UNCLE UNDER UNITY UNTIL UPPER UPSET URBAN USUAL VALUE VIDEO VISIT VITAL
        VOICE WAGON WASTE WATCH WATER WHALE WHEAT WHEEL WHERE WHILE WHITE WHOLE WOMAN WORLD WORRY WORTH WRITE
        YOUNG YOUTH
    """.trim().split(Regex("\\s+"))

    val dictionary: Set<String> by lazy { (words + common).toHashSet() }
}
