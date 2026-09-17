package com.jedflix.tv.data.live

import com.jedflix.tv.data.tmdb.MediaType

object LiveChannels {
    const val MARVEL = "marvel"
    const val CARTOONS_90S = "90s-cartoons"
    const val CARTOON_NETWORK = "cartoon-network"
    const val SITCOM = "sitcom"
    const val OFFICE_247 = "office-247"
    const val COMEDY_CENTRAL = "comedy-central"
    const val AE = "ae"
    const val FOOD_NETWORK = "food-network"
    const val DISNEY_XD = "disney-xd"
    const val STAR_WARS = "star-wars"
    const val HARRY_POTTER = "harry-potter"
    const val HGTV = "hgtv"
    const val SIMPSONS = "simpsons"
    const val DISCOVERY = "discovery"
    const val SEINFELD = "seinfeld"
    const val BROOKLYN_NINE_NINE = "brooklyn-nine-nine"
    const val IMPRACTICAL_JOKERS = "impractical-jokers"
    const val SEX_AND_THE_CITY = "sex-and-the-city"

    val defaultId: String = MARVEL

    val all: List<LiveChannel> = listOf(
        marvel,
        cartoons90s,
        comedyCentral,
        sitcom,
        harryPotter,
        ae,
        sexAndTheCity,
        impracticalJokers,
        discovery,
        hgtv,
        foodNetwork,
        starWars,
        seinfeld,
        disneyXd,
        brooklynNineNine,
        office247,
        simpsons,
        cartoonNetwork,
    )

    fun byId(id: String): LiveChannel? = all.firstOrNull { it.id == id }

    fun require(id: String): LiveChannel = byId(id) ?: all.first()
}

private val marvel = LiveChannel(
    id = LiveChannels.MARVEL,
    name = "Marvel",
    lineup = listOf(
        movie(1726, "Iron Man", 126),
        movie(10138, "Iron Man 2", 124),
        movie(10195, "Thor", 115),
        movie(1771, "Captain America: The First Avenger", 124),
        movie(24428, "The Avengers", 143),
        movie(68721, "Iron Man 3", 130),
        movie(100402, "Captain America: The Winter Soldier", 136),
        movie(118340, "Guardians of the Galaxy", 121),
        movie(99861, "Avengers: Age of Ultron", 141),
        movie(271110, "Captain America: Civil War", 147),
        movie(284052, "Doctor Strange", 115),
        movie(284054, "Black Panther", 134),
        movie(299536, "Avengers: Infinity War", 149),
        movie(299534, "Avengers: Endgame", 181),
        movie(634649, "Spider-Man: No Way Home", 148),
    ),
)

private val cartoonNetwork = LiveChannel(
    id = LiveChannels.CARTOON_NETWORK,
    name = "Cartoon Network",
    lineup = listOf(
        episode(15260, 1, 1, "Adventure Time  •  Slumber Party Panic", 11),
        episode(15260, 1, 2, "Adventure Time  •  Trouble in Lumpy Space", 11),
        episode(15260, 1, 3, "Adventure Time  •  Prisoners of Love", 11),
        episode(1424, 1, 1, "Regular Show  •  The Power", 11),
        episode(1424, 1, 2, "Regular Show  •  Just Set Up the Chairs", 11),
        episode(1424, 1, 3, "Regular Show  •  Caffeinated Concert Tickets", 11),
        episode(61374, 1, 1, "Steven Universe  •  Gem Glow", 11),
        episode(61374, 1, 2, "Steven Universe  •  Laser Light Cannon", 11),
        episode(61617, 1, 1, "Over the Garden Wall  •  The Old Grist Mill", 11),
        episode(61617, 1, 2, "Over the Garden Wall  •  Hard Times at the Huskin' Bee", 11),
        episode(15260, 1, 4, "Adventure Time  •  Tree Trunks", 11),
        episode(1424, 1, 4, "Regular Show  •  Death Punchies", 11),
    ),
)

private val sitcom = LiveChannel(
    id = LiveChannels.SITCOM,
    name = "Sitcom",
    lineup = listOf(
        episode(1668, 1, 1, "Friends  •  The One Where Monica Gets a Roommate", 22),
        episode(1668, 1, 2, "Friends  •  The One with the Sonogram at the End", 22),
        episode(2316, 1, 1, "The Office  •  Pilot", 22),
        episode(2316, 1, 2, "The Office  •  Diversity Day", 22),
        episode(48891, 1, 1, "Brooklyn Nine-Nine  •  Pilot", 22),
        episode(48891, 1, 2, "Brooklyn Nine-Nine  •  The Tagger", 22),
        episode(8592, 1, 1, "Parks and Recreation  •  Pilot", 22),
        episode(8592, 1, 2, "Parks and Recreation  •  Canvassing", 22),
        episode(1668, 1, 3, "Friends  •  The One with the Thumb", 22),
        episode(2316, 1, 3, "The Office  •  Health Care", 22),
        episode(48891, 1, 3, "Brooklyn Nine-Nine  •  The Slump", 22),
        episode(8592, 1, 3, "Parks and Recreation  •  The Reporter", 22),
    ),
)

private val office247 = LiveChannel(
    id = LiveChannels.OFFICE_247,
    name = "The Office 24/7",
    lineup = listOf(
        episode(2316, 1, 1, "The Office  •  Pilot", 22),
        episode(2316, 1, 2, "The Office  •  Diversity Day", 22),
        episode(2316, 1, 3, "The Office  •  Health Care", 22),
        episode(2316, 1, 4, "The Office  •  The Alliance", 22),
        episode(2316, 1, 5, "The Office  •  Basketball", 22),
        episode(2316, 1, 6, "The Office  •  Hot Girl", 22),
        episode(2316, 2, 1, "The Office  •  The Dundies", 22),
        episode(2316, 2, 2, "The Office  •  Sexual Harassment", 22),
        episode(2316, 2, 3, "The Office  •  Office Olympics", 22),
        episode(2316, 2, 4, "The Office  •  The Fire", 22),
        episode(2316, 2, 5, "The Office  •  Halloween", 22),
        episode(2316, 2, 6, "The Office  •  The Fight", 22),
        episode(2316, 2, 7, "The Office  •  The Client", 22),
        episode(2316, 2, 8, "The Office  •  Performance Review", 22),
        episode(2316, 2, 9, "The Office  •  Email Surveillance", 22),
        episode(2316, 2, 10, "The Office  •  Christmas Party", 22),
        episode(2316, 2, 11, "The Office  •  Booze Cruise", 22),
        episode(2316, 2, 12, "The Office  •  The Injury", 22),
        episode(2316, 3, 1, "The Office  •  Gay Witch Hunt", 22),
        episode(2316, 3, 2, "The Office  •  The Convention", 22),
        episode(2316, 3, 3, "The Office  •  The Coup", 22),
        episode(2316, 3, 12, "The Office  •  Traveling Salesmen", 22),
        episode(2316, 3, 13, "The Office  •  The Return", 22),
        episode(2316, 3, 22, "The Office  •  Beach Games", 22),
    ),
)

private val comedyCentral = LiveChannel(
    id = LiveChannels.COMEDY_CENTRAL,
    name = "Comedy Central",
    lineup = listOf(
        episode(2190, 1, 1, "South Park  •  Cartman Gets an Anal Probe", 22),
        episode(2190, 1, 2, "South Park  •  Volcano", 22),
        episode(2710, 1, 1, "It's Always Sunny  •  The Gang Gets Racist", 22),
        episode(43082, 1, 1, "Key & Peele  •  I Said Bitch", 22),
        episode(36994, 1, 3, "Workaholics  •  Office Campout", 22),
        episode(58957, 1, 1, "Nathan for You  •  Yogurt Shop / Pizzeria", 22),
        episode(60839, 1, 1, "Broad City  •  What a Wonderful World", 22),
        episode(2190, 1, 3, "South Park  •  Weight Gain 4000", 22),
        episode(2710, 1, 5, "It's Always Sunny  •  Gun Fever", 22),
        episode(43082, 1, 2, "Key & Peele  •  Black Hawk Up", 22),
        episode(36994, 1, 4, "Workaholics  •  The Promotion", 22),
        episode(58957, 1, 2, "Nathan for You  •  Santa / Petting Zoo", 22),
        episode(60839, 1, 3, "Broad City  •  Working Girls", 22),
        episode(2190, 1, 7, "South Park  •  Pinkeye", 22),
        episode(2710, 1, 6, "It's Always Sunny  •  The Gang Finds a Dead Guy", 22),
        episode(43082, 1, 4, "Key & Peele  •  The Branding", 22),
    ),
)

private val ae = LiveChannel(
    id = LiveChannels.AE,
    name = "A&E",
    lineup = listOf(
        episode(34971, 1, 1, "Storage Wars  •  High Noon in the High Desert", 22),
        episode(42738, 1, 1, "Duck Dynasty  •  Family Funny Business", 22),
        episode(96152, 1, 1, "Court Cam  •  Episode 1", 22),
        episode(74818, 1, 1, "Border Security  •  Episode 1", 22),
        episode(68430, 1, 1, "Live PD  •  10.28.16", 60),
        episode(30946, 1, 1, "Hoarders  •  Jennifer & Ron/Jill", 60),
        episode(34971, 1, 2, "Storage Wars  •  Railroad Roulette", 22),
        episode(42738, 1, 2, "Duck Dynasty  •  CEO for a Day", 22),
        episode(96152, 1, 2, "Court Cam  •  Episode 2", 22),
        episode(74818, 1, 2, "Border Security  •  Episode 2", 22),
        episode(34971, 1, 3, "Storage Wars  •  Melee in the Maze", 22),
        episode(42738, 1, 3, "Duck Dynasty  •  High Tech Redneck", 22),
        episode(30946, 1, 2, "Hoarders  •  Linda & Steven", 60),
        episode(68430, 1, 2, "Live PD  •  11.04.16", 60),
    ),
)

private val foodNetwork = LiveChannel(
    id = LiveChannels.FOOD_NETWORK,
    name = "Food Network",
    lineup = listOf(
        episode(17404, 1, 1, "Chopped  •  Octopus, Duck, Animal Crackers", 42),
        episode(4086, 1, 1, "Diners, Drive-Ins and Dives  •  Classics", 22),
        episode(57022, 1, 1, "Cutthroat Kitchen  •  Vive le Sabotage", 42),
        episode(62481, 1, 1, "Beat Bobby Flay  •  Grueneberg and Nunziata", 22),
        episode(62595, 1, 1, "Guy's Grocery Games  •  Wild in the Aisles", 42),
        episode(4086, 1, 2, "Diners, Drive-Ins and Dives  •  That's Italian", 22),
        episode(17404, 1, 2, "Chopped  •  Tofu, Blueberries, Oysters", 42),
        episode(62481, 1, 2, "Beat Bobby Flay  •  Welcome to New York!", 22),
        episode(4086, 1, 5, "Diners, Drive-Ins and Dives  •  BBQ", 22),
        episode(57022, 1, 2, "Cutthroat Kitchen  •  Pork Chops and Sabotage", 42),
        episode(62595, 1, 2, "Guy's Grocery Games  •  Frozen Feats", 42),
        episode(4086, 1, 7, "Diners, Drive-Ins and Dives  •  Burgers", 22),
    ),
)

private val disneyXd = LiveChannel(
    id = LiveChannels.DISNEY_XD,
    name = "Disney XD",
    lineup = listOf(
        episode(40075, 1, 1, "Gravity Falls  •  Tourist Trapped", 22),
        episode(1877, 1, 1, "Phineas and Ferb  •  Rollercoaster", 22),
        episode(17572, 1, 1, "Kick Buttowski  •  Dead Man's Drop", 22),
        episode(38867, 1, 1, "Lab Rats  •  Crush, Chop and Burn", 22),
        episode(31628, 1, 1, "Pair of Kings  •  Return of the Kings", 22),
        episode(61923, 1, 1, "Star vs. the Forces of Evil  •  Star Comes to Earth", 22),
        episode(45013, 1, 1, "Wander Over Yonder  •  The Greatest", 22),
        episode(72350, 1, 1, "DuckTales  •  Woo-oo!", 22),
        episode(32716, 1, 1, "Kickin' It  •  Wasabi Warriors", 22),
        episode(57961, 1, 1, "Mighty Med  •  Saving the People Who Save People", 22),
        episode(40075, 1, 2, "Gravity Falls  •  The Legend of the Gobblewonker", 22),
        episode(1877, 1, 2, "Phineas and Ferb  •  Lawn Gnome Beach Party of Terror", 22),
        episode(67549, 1, 1, "Milo Murphy's Law  •  Going the Extra Milo", 22),
        episode(62486, 1, 1, "Penn Zero  •  The Zero You Know", 22),
        episode(40075, 1, 4, "Gravity Falls  •  The Hand That Rocks the Mabel", 22),
        episode(1877, 1, 3, "Phineas and Ferb  •  Flop Starz", 22),
    ),
)

private val starWars = LiveChannel(
    id = LiveChannels.STAR_WARS,
    name = "Star Wars",
    lineup = listOf(
        movie(11, "Star Wars", 121),
        movie(1891, "The Empire Strikes Back", 124),
        movie(1892, "Return of the Jedi", 131),
        episode(4194, 1, 1, "The Clone Wars  •  Ambush", 22),
        episode(4194, 1, 2, "The Clone Wars  •  Rising Malevolence", 22),
        movie(1893, "The Phantom Menace", 136),
        movie(1894, "Attack of the Clones", 142),
        movie(1895, "Revenge of the Sith", 140),
        episode(60554, 1, 1, "Rebels  •  Spark of Rebellion", 44),
        movie(330459, "Rogue One", 133),
        movie(140607, "The Force Awakens", 138),
        episode(82856, 1, 1, "The Mandalorian  •  Chapter 1: The Mandalorian", 40),
        movie(181808, "The Last Jedi", 152),
        movie(348350, "Solo", 135),
        movie(181812, "The Rise of Skywalker", 142),
    ),
)

private val harryPotter = LiveChannel(
    id = LiveChannels.HARRY_POTTER,
    name = "Harry Potter",
    lineup = listOf(
        movie(671, "Harry Potter and the Philosopher's Stone", 152),
        movie(672, "Harry Potter and the Chamber of Secrets", 161),
        movie(673, "Harry Potter and the Prisoner of Azkaban", 141),
        movie(674, "Harry Potter and the Goblet of Fire", 157),
        movie(675, "Harry Potter and the Order of the Phoenix", 138),
        movie(767, "Harry Potter and the Half-Blood Prince", 153),
        movie(12444, "Harry Potter and the Deathly Hallows: Part 1", 146),
        movie(12445, "Harry Potter and the Deathly Hallows: Part 2", 130),
        movie(259316, "Fantastic Beasts and Where to Find Them", 133),
        movie(338952, "Fantastic Beasts: The Crimes of Grindelwald", 134),
        movie(671, "Harry Potter and the Philosopher's Stone", 152),
        movie(673, "Harry Potter and the Prisoner of Azkaban", 141),
    ),
)

private val hgtv = LiveChannel(
    id = LiveChannels.HGTV,
    name = "HGTV",
    lineup = listOf(
        episode(6480, 1, 1, "House Hunters  •  Episode 1", 22),
        episode(33927, 1, 1, "Love It or List It  •  Episode 1", 42),
        episode(35058, 1, 1, "Property Brothers  •  Episode 1", 42),
        episode(6480, 1, 2, "House Hunters  •  Episode 2", 22),
        episode(33927, 1, 2, "Love It or List It  •  Episode 2", 42),
        episode(35058, 1, 2, "Property Brothers  •  Episode 2", 42),
        episode(6480, 1, 3, "House Hunters  •  Episode 3", 22),
        episode(33927, 1, 3, "Love It or List It  •  Episode 3", 42),
        episode(35058, 1, 3, "Property Brothers  •  Episode 3", 42),
        episode(6480, 2, 1, "House Hunters  •  Episode 4", 22),
        episode(33927, 1, 4, "Love It or List It  •  Episode 4", 42),
        episode(35058, 1, 4, "Property Brothers  •  Episode 4", 42),
        episode(6480, 2, 2, "House Hunters  •  Episode 5", 22),
        episode(35058, 1, 5, "Property Brothers  •  Episode 5", 42),
    ),
)

private val simpsons = LiveChannel(
    id = LiveChannels.SIMPSONS,
    name = "The Simpsons",
    lineup = listOf(
        episode(456, 1, 1, "The Simpsons  •  Simpsons Roasting on an Open Fire", 22),
        episode(456, 2, 12, "The Simpsons  •  The Way We Was", 22),
        episode(456, 4, 12, "The Simpsons  •  Marge vs. the Monorail", 22),
        episode(456, 4, 17, "The Simpsons  •  Last Exit to Springfield", 22),
        episode(456, 5, 2, "The Simpsons  •  Cape Feare", 22),
        episode(456, 5, 8, "The Simpsons  •  Boy-Scoutz 'n the Hood", 22),
        episode(456, 5, 10, "The Simpsons  •  \$pringfield", 22),
        episode(456, 6, 6, "The Simpsons  •  Treehouse of Horror V", 22),
        episode(456, 6, 12, "The Simpsons  •  Homer the Great", 22),
        episode(456, 7, 6, "The Simpsons  •  Treehouse of Horror VI", 22),
        episode(456, 8, 14, "The Simpsons  •  The Itchy & Scratchy & Poochie Show", 22),
        episode(456, 8, 23, "The Simpsons  •  Homer's Enemy", 22),
        episode(456, 2, 11, "The Simpsons  •  One Fish, Two Fish, Blowfish, Blue Fish", 22),
        episode(456, 4, 3, "The Simpsons  •  Homer the Heretic", 22),
        episode(456, 5, 1, "The Simpsons  •  Homer's Barbershop Quartet", 22),
        episode(456, 9, 2, "The Simpsons  •  The Principal and the Pauper", 22),
    ),
)

private val discovery = LiveChannel(
    id = LiveChannels.DISCOVERY,
    name = "Discovery",
    lineup = listOf(
        episode(1428, 1, 1, "MythBusters  •  Jet-Assisted Chevy", 44),
        episode(1749, 1, 1, "How It's Made  •  Episode 1", 22),
        episode(1428, 1, 2, "MythBusters  •  Poppy-Seed Drug Test / Snowblower / Exploding Toilet", 44),
        episode(1749, 1, 2, "How It's Made  •  Episode 2", 22),
        episode(1428, 1, 3, "MythBusters  •  Barrel of Bricks / Cell Phone Destruction / Hammer vs. Honey", 44),
        episode(1749, 1, 3, "How It's Made  •  Episode 3", 22),
        episode(1428, 1, 4, "MythBusters  •  Penny Drop / Buried Alive / Cola", 44),
        episode(1749, 1, 4, "How It's Made  •  Episode 4", 22),
        episode(1428, 2, 1, "MythBusters  •  Explosive Decompression / Frog Giggin' / Rear Axle", 44),
        episode(1749, 1, 5, "How It's Made  •  Episode 5", 22),
        episode(1428, 2, 5, "MythBusters  •  Exploding Jawbreaker / Frozen Airline Food / Vacuum Toilet", 44),
        episode(1749, 2, 1, "How It's Made  •  Episode 6", 22),
        episode(1428, 3, 1, "MythBusters  •  Brown Note / Cell Phones on Planes / Killer Tissue Box", 44),
        episode(1749, 2, 2, "How It's Made  •  Episode 7", 22),
    ),
)

private val cartoons90s = LiveChannel(
    id = LiveChannels.CARTOONS_90S,
    name = "90s/2000s Cartoons",
    lineup = listOf(
        episode(387, 1, 1, "SpongeBob SquarePants  •  Help Wanted", 11),
        episode(3022, 1, 1, "Rugrats  •  Tommy's First Birthday", 22),
        episode(4229, 1, 1, "Dexter's Laboratory  •  Dee Deemensional", 11),
        episode(607, 1, 1, "The Powerpuff Girls  •  Meat Fuzzy Lumpkins", 11),
        episode(606, 1, 1, "Ed, Edd n Eddy  •  The Ed-touchables", 11),
        episode(4630, 1, 1, "The Fairly OddParents  •  The Big Problem", 11),
        episode(537, 1, 1, "Hey Arnold!  •  Downtown as Fruits", 11),
        episode(2085, 1, 1, "Courage the Cowardly Dog  •  A Night at the Katz Motel", 11),
        episode(3793, 1, 1, "Invader Zim  •  The Nightmare Begins", 22),
        episode(2345, 1, 1, "Kim Possible  •  Crush", 22),
        episode(604, 1, 1, "Teen Titans  •  Divide and Conquer", 22),
        episode(246, 1, 1, "Avatar: The Last Airbender  •  The Boy in the Iceberg", 22),
        episode(1546, 1, 1, "Recess  •  The Break In", 11),
        episode(2405, 1, 1, "Johnny Bravo  •  Date with an Ant", 11),
        episode(543, 1, 1, "The Proud Family  •  Bring It On", 22),
        episode(1567, 1, 1, "CatDog  •  Dog Gone", 11),
        episode(387, 1, 2, "SpongeBob SquarePants  •  Bubblestand", 11),
        episode(607, 1, 2, "The Powerpuff Girls  •  Insect Inside", 11),
    ),
)

private val seinfeld = LiveChannel(
    id = LiveChannels.SEINFELD,
    name = "Seinfeld",
    lineup = listOf(
        episode(1400, 1, 1, "Seinfeld  •  The Seinfeld Chronicles", 22),
        episode(1400, 2, 1, "Seinfeld  •  The Ex-Girlfriend", 22),
        episode(1400, 2, 6, "Seinfeld  •  The Chinese Restaurant", 22),
        episode(1400, 3, 6, "Seinfeld  •  The Parking Garage", 22),
        episode(1400, 3, 11, "Seinfeld  •  The Alternate Side", 22),
        episode(1400, 4, 3, "Seinfeld  •  The Pitch", 22),
        episode(1400, 4, 11, "Seinfeld  •  The Contest", 22),
        episode(1400, 4, 24, "Seinfeld  •  The Pilot", 22),
        episode(1400, 5, 2, "Seinfeld  •  The Puffy Shirt", 22),
        episode(1400, 5, 6, "Seinfeld  •  The Non-Fat Yogurt", 22),
        episode(1400, 6, 14, "Seinfeld  •  The Beard", 22),
        episode(1400, 7, 6, "Seinfeld  •  The Soup Nazi", 22),
        episode(1400, 8, 13, "Seinfeld  •  The Comeback", 22),
        episode(1400, 9, 16, "Seinfeld  •  The Puerto Rican Day", 22),
        episode(1400, 3, 4, "Seinfeld  •  The Library", 22),
        episode(1400, 4, 17, "Seinfeld  •  The Outing", 22),
    ),
)

private val brooklynNineNine = LiveChannel(
    id = LiveChannels.BROOKLYN_NINE_NINE,
    name = "Brooklyn Nine-Nine",
    lineup = listOf(
        episode(48891, 1, 1, "Brooklyn Nine-Nine  •  Pilot", 22),
        episode(48891, 1, 2, "Brooklyn Nine-Nine  •  The Tagger", 22),
        episode(48891, 1, 3, "Brooklyn Nine-Nine  •  The Slump", 22),
        episode(48891, 1, 4, "Brooklyn Nine-Nine  •  M.E. Time", 22),
        episode(48891, 1, 5, "Brooklyn Nine-Nine  •  The Vulture", 22),
        episode(48891, 1, 6, "Brooklyn Nine-Nine  •  Halloween", 22),
        episode(48891, 1, 7, "Brooklyn Nine-Nine  •  48 Hours", 22),
        episode(48891, 1, 13, "Brooklyn Nine-Nine  •  The Bet", 22),
        episode(48891, 1, 15, "Brooklyn Nine-Nine  •  Christmas", 22),
        episode(48891, 2, 1, "Brooklyn Nine-Nine  •  Undercover", 22),
        episode(48891, 2, 4, "Brooklyn Nine-Nine  •  Halloween II", 22),
        episode(48891, 2, 7, "Brooklyn Nine-Nine  •  The Pontiac Bandit Returns", 22),
        episode(48891, 3, 5, "Brooklyn Nine-Nine  •  Halloween III", 22),
        episode(48891, 4, 5, "Brooklyn Nine-Nine  •  Halloween IV", 22),
        episode(48891, 5, 4, "Brooklyn Nine-Nine  •  HalloVeen", 22),
        episode(48891, 6, 4, "Brooklyn Nine-Nine  •  He Said, She Said", 22),
    ),
)

private val impracticalJokers = LiveChannel(
    id = LiveChannels.IMPRACTICAL_JOKERS,
    name = "Impractical Jokers",
    lineup = listOf(
        episode(59186, 1, 1, "Impractical Jokers  •  A Loser Walks Into a Bar", 22),
        episode(59186, 1, 2, "Impractical Jokers  •  The Talking Doll", 22),
        episode(59186, 1, 3, "Impractical Jokers  •  From the Joker Files", 22),
        episode(59186, 1, 4, "Impractical Jokers  •  Superhero", 22),
        episode(59186, 1, 5, "Impractical Jokers  •  Off the Tables", 22),
        episode(59186, 1, 6, "Impractical Jokers  •  Look Out Below", 22),
        episode(59186, 2, 1, "Impractical Jokers  •  Elephant in the Room", 22),
        episode(59186, 2, 2, "Impractical Jokers  •  The Stoop Session", 22),
        episode(59186, 2, 5, "Impractical Jokers  •  The Truth Hurts", 22),
        episode(59186, 3, 1, "Impractical Jokers  •  The Perfect Storm", 22),
        episode(59186, 3, 4, "Impractical Jokers  •  The Lost Episode", 22),
        episode(59186, 4, 1, "Impractical Jokers  •  Welcome to the Dark Side", 22),
        episode(59186, 5, 1, "Impractical Jokers  •  Hellcoptour", 22),
        episode(59186, 6, 1, "Impractical Jokers  •  True Dromance", 22),
    ),
)

private val sexAndTheCity = LiveChannel(
    id = LiveChannels.SEX_AND_THE_CITY,
    name = "Sex and the City",
    lineup = listOf(
        episode(105, 1, 1, "Sex and the City  •  Sex and the City", 30),
        episode(105, 1, 2, "Sex and the City  •  Models and Mortals", 30),
        episode(105, 1, 3, "Sex and the City  •  Bay of Married Pigs", 30),
        episode(105, 1, 4, "Sex and the City  •  Valley of the Twenty-Something Guys", 30),
        episode(105, 1, 5, "Sex and the City  •  The Power of Female Sex", 30),
        episode(105, 1, 9, "Sex and the City  •  The Drought", 30),
        episode(105, 2, 1, "Sex and the City  •  Take Me Out to the Ballgame", 30),
        episode(105, 2, 18, "Sex and the City  •  Ex and the City", 30),
        episode(105, 3, 4, "Sex and the City  •  Boy, Girl, Boy, Girl", 30),
        episode(105, 4, 1, "Sex and the City  •  The Agony and the Ex-tacy", 30),
        episode(105, 5, 1, "Sex and the City  •  Anchors Away", 30),
        episode(105, 6, 1, "Sex and the City  •  To Market, to Market", 30),
        episode(105, 6, 12, "Sex and the City  •  Catch-38", 30),
        episode(105, 6, 20, "Sex and the City  •  An American Girl in Paris, Part Deux", 30),
    ),
)

private fun movie(tmdbId: Int, title: String, minutes: Int): LiveProgram = LiveProgram(
    mediaType = MediaType.MOVIE,
    tmdbId = tmdbId,
    displayTitle = title,
    durationMs = minutes * 60_000L,
)

private fun episode(
    tmdbId: Int,
    season: Int,
    number: Int,
    title: String,
    minutes: Int,
): LiveProgram = LiveProgram(
    mediaType = MediaType.TV,
    tmdbId = tmdbId,
    displayTitle = title,
    durationMs = minutes * 60_000L,
    season = season,
    episode = number,
)
