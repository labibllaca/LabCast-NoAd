package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PodcastRepository(private val podcastDao: PodcastDao) {

    val allPodcasts: Flow<List<PodcastEntity>> = podcastDao.getAllPodcasts()
    val allEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getAllEpisodes()
    val downloadedEpisodes: Flow<List<EpisodeEntity>> = podcastDao.getDownloadedEpisodes()
    val syncLogs: Flow<List<SyncLogEntity>> = podcastDao.getSyncLogs()

    fun getEpisodesForPodcast(podcastId: String): Flow<List<EpisodeEntity>> {
        return podcastDao.getEpisodesForPodcast(podcastId)
    }

    suspend fun getPodcastById(id: String): PodcastEntity? {
        return podcastDao.getPodcastById(id)
    }

    suspend fun getEpisodeById(id: String): EpisodeEntity? {
        return podcastDao.getEpisodeById(id)
    }

    suspend fun updateEpisodeProgress(episodeId: String, positionMs: Long, isCompleted: Boolean) {
        podcastDao.updatePlaybackProgress(episodeId, positionMs, isCompleted)
    }

    suspend fun clearAllHistory() {
        podcastDao.clearAllHistory()
    }

    suspend fun clearEpisodeHistory(episodeId: String) {
        podcastDao.clearEpisodeHistory(episodeId)
    }

    suspend fun updateDownloadStatus(episodeId: String, isDownloaded: Boolean, localPath: String?) {
        podcastDao.updateDownloadStatus(episodeId, isDownloaded, localPath)
    }

    suspend fun updateEpisode(episode: EpisodeEntity) {
        podcastDao.updateEpisode(episode)
    }

    suspend fun updatePodcast(podcast: PodcastEntity) {
        podcastDao.updatePodcast(podcast)
    }

    suspend fun insertPodcast(podcast: PodcastEntity) {
        podcastDao.insertPodcasts(listOf(podcast))
    }

    suspend fun insertEpisodes(episodes: List<EpisodeEntity>) {
        podcastDao.insertEpisodes(episodes)
    }

    suspend fun addSyncLog(deviceName: String, action: String) {
        podcastDao.insertSyncLog(SyncLogEntity(deviceName = deviceName, action = action))
    }

    suspend fun clearSyncLogs() {
        podcastDao.clearSyncLogs()
    }

    // Population of Initial Rich Data
    suspend fun populateInitialDataIfNeeded() {
        val currentPodcasts = allPodcasts.first()
        if (currentPodcasts.any { it.id == "pod_huberman_1545953110" }) return

        val defaultPodcasts = listOf(
            PodcastEntity(
                id = "pod_huberman_1545953110",
                title = "Huberman Lab",
                author = "Scicomm Media / Dr. Andrew Huberman",
                description = "The Huberman Lab podcast discusses neuroscience and science-based tools, including how our brain and its connections with the organs of our body control our perceptions, our behaviors, and our health.",
                coverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts113/v4/31/34/00/31340019-3f0e-e377-df35-18151c6ef0ad/mza_10793616858548971277.jpg/600x600bb.jpg",
                category = "Health & Fitness",
                isSubscribed = true,
                feedUrl = "https://feeds.megaphone.fm/hubermanlab"
            ),
            PodcastEntity(
                id = "pod_shqip_1521438307",
                title = "Shqip Story Podcast",
                author = "Hasbije B.",
                description = "Një hapësirë ku dëgjohen historitë, përvojat dhe narrativat autentike shqiptare. Histori jetësore, investigative dhe kulturore.",
                coverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts115/v4/ed/d8/7b/edd87b4a-243e-c2b8-c8d7-78a991ab8f13/mza_14377614502294035074.jpg/600x600bb.jpg",
                category = "True Crime",
                isSubscribed = true,
                feedUrl = "https://anchor.fm/s/28d45cb0/podcast/rss"
            ),
            PodcastEntity(
                id = "pod_harbinger_1344999619",
                title = "The Jordan Harbinger Show",
                author = "Jordan Harbinger",
                description = "In-depth conversations with the world's top performers, legendary thinkers, and fascinating minds, deconstructing their strategies and practical wisdom.",
                coverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/ce/57/a9/ce57a912-523d-5e81-f461-c71591eb2b4b/mza_855972047978822038.jpeg/600x600bb.jpg",
                category = "Education",
                isSubscribed = true,
                feedUrl = "https://rss.introcast.io:443/1344999619/www.podcastone.com/podcast?categoryID2=1237"
            ),
            PodcastEntity(
                id = "pod_aom_332516054",
                title = "The Art of Manliness",
                author = "Brett McKay",
                description = "Insights on philosophy, history, fitness, literature, psychology, and personal growth to help you live a flourishing and capable life.",
                coverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/ec/db/72/ecdb72bd-11e5-5c9b-87a6-a8f157b214db/mza_11005987414919781126.jpeg/600x600bb.jpg",
                category = "Philosophy",
                isSubscribed = true,
                feedUrl = "https://rss.art19.com/the-art-of-manliness"
            ),
            PodcastEntity(
                id = "pod_peterson_1184022695",
                title = "The Jordan B. Peterson Podcast",
                author = "Dr. Jordan B. Peterson",
                description = "Lectures, interviews, and deep philosophical discussions exploring psychology, culture, meaning, theology, and the human condition.",
                coverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/58/16/42/581642ef-7f31-d538-7c69-0a42ec25c604/mza_5721699391369703653.jpeg/600x600bb.jpg",
                category = "Education",
                isSubscribed = true,
                feedUrl = "https://feeds.megaphone.fm/BVDWV6444647327"
            ),
            PodcastEntity(
                id = "pod_batman_1802737962",
                title = "DC High Volume: Batman",
                author = "DC | Realm",
                description = "An immersive, cinematic audio experience following Batman as he faces dark conspiracies, psychological battles, and Gotham's greatest rogues.",
                coverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/17/2c/78/172c787f-79ce-80e7-cec2-2f08d6f1cbb1/mza_17006185782027313788.jpeg/600x600bb.jpg",
                category = "Fiction",
                isSubscribed = true,
                feedUrl = "https://feeds.megaphone.fm/SBP4487706450"
            )
        )

        val realAudioUrls = listOf(
            "https://traffic.megaphone.fm/SCIM7156610982.mp3",
            "https://traffic.megaphone.fm/SCIM7393383815.mp3",
            "https://traffic.megaphone.fm/SCIM2465421786.mp3",
            "https://traffic.megaphone.fm/SCIM3386045656.mp3",
            "https://traffic.megaphone.fm/SCIM7816635332.mp3",
            "https://traffic.megaphone.fm/SBP4487706450.mp3"
        )

        val defaultEpisodes = listOf(
            EpisodeEntity(
                id = "ep_huberman_1",
                podcastId = "pod_huberman_1545953110",
                podcastTitle = "Huberman Lab",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts113/v4/31/34/00/31340019-3f0e-e377-df35-18151c6ef0ad/mza_10793616858548971277.jpg/600x600bb.jpg",
                title = "Essentials: Use Sleep to Enhance Learning, Memory & Emotional State | Dr. Gina Poe",
                description = "In this Huberman Lab Essentials episode, Dr. Gina Poe, Professor of Integrative Biology and Physiology at UCLA, discusses the architecture of sleep, memory consolidation, and tools to optimize deep recovery.",
                durationSeconds = 2040, // 34 minutes
                publishDate = "2026-09-03",
                audioUrl = realAudioUrls[0],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "407,1196,1718",
                chapters = "0:Dr. Gina Poe Introduction|19:Sleep States & Perfect Night's Sleep|143:Early Sleep & Memory Processing|274:Growth Hormone & Consistent Bedtime|407:Sponsor: LMNT|500:Alcohol & Negative Sleep Effects|558:Middle Sleep States & Creativity|632:Waking During Night & Hydration|699:REM, Deep Sleep & Sleepwalking|841:Morning Grogginess & Trackers|953:Brain Waste Clearance & Glial Flow|1196:Sponsor: Eight Sleep|1274:Locus Coeruleus & Calm Bedtime Routine|1516:Sleep Spindles & Learning|1718:Sponsor: AG1|1796:Trauma Recovery & REM Sleep|2009:Acknowledgements & Disclaimers",
                transcript = "0:00 [Host] Welcome to Huberman Lab. Today we are joined by Dr. Gina Poe to discuss the architecture of sleep.\n00:45 [Sponsor Break] This episode is brought to you by AG1 and LMNT. AG1 is your daily foundational nutrition drink. Use promo code HUBERMAN for 20% off.\n02:23 [Host] Dr. Poe, let's start with how deep sleep consolidates memories.\n06:47 [Host [Sponsor]] Quick break for our sponsor Eight Sleep. The Pod 4 Ultra cover regulates temperature dynamically while you sleep.\n19:56 [Host [Sponsor]] Brought to you by LMNT zero-sugar hydration electrolytes. Visit drinklmnt.com/huberman.\n25:10 [Guest] As we move into REM sleep, the brain actively strips emotional charge from difficult memories."
            ),
            EpisodeEntity(
                id = "ep_huberman_2",
                podcastId = "pod_huberman_1545953110",
                podcastTitle = "Huberman Lab",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts113/v4/31/34/00/31340019-3f0e-e377-df35-18151c6ef0ad/mza_10793616858548971277.jpg/600x600bb.jpg",
                title = "Master Your Dopamine & Drive for Focus, Motivation & Performance",
                description = "Learn how dopamine governs motivation, energy levels, craving, and neuroplasticity. Dr. Huberman outlines science-backed behavioral and environmental protocols to sustain high drive without burnout.",
                durationSeconds = 2400, // 40 mins
                publishDate = "2026-08-27",
                audioUrl = realAudioUrls[1],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "180,960",
                chapters = "0:Dopamine Dynamics Overview|180:Sponsor: Athletic Greens|270:The Dopamine Baseline & Peaks|540:Effort and the Reward Circuit|960:Sponsor: InsideTracker|1050:Cold Exposure & Neurotransmitter Release|1500:Intermittent Reward Schedules|2100:Actionable Protocols & Summary",
                transcript = "0:00 [Host] Welcome back. Today's deep dive is centered on dopamine dynamics and focus.\n03:00 [Sponsor Break] Brought to you by Athletic Greens AG1. Nutrient-dense daily greens for immune support and gut health.\n04:30 [Host] Understanding baseline dopamine versus peak dopamine is critical for long-term motivation.\n16:00 [Sponsor Break] Today's episode is sponsored by InsideTracker. Personalized biometric blood analysis for optimal performance.\n25:00 [Host] Cold exposure triggers a sustained 250% increase in baseline dopamine and epinephrine."
            ),
            EpisodeEntity(
                id = "ep_shqip_1",
                podcastId = "pod_shqip_1521438307",
                podcastTitle = "Shqip Story Podcast",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts115/v4/ed/d8/7b/edd87b4a-243e-c2b8-c8d7-78a991ab8f13/mza_14377614502294035074.jpg/600x600bb.jpg",
                title = "Misteret e Pazgjidhura dhe Rrëfimet e Ndaluara",
                description = "Një hetim i thellë mbi ngjarje të pazakonta dhe histori të padëgjuara më parë. Rrëfime autentike nga dëshmitarë të kohës.",
                durationSeconds = 1800, // 30 mins
                publishDate = "2026-08-30",
                audioUrl = realAudioUrls[2],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "120,600",
                chapters = "0:Hyrje dhe Ngjarja Kryesore|120:Sponsor: Njoftime & Partnerë|210:Dëshmitë e Para|600:Reklamë / Sponsor|690:Zhvillimet e Hetimit|1200:Konkluzionet dhe Mesazhi Përfundimtar",
                transcript = "0:00 [Mprehësi] Mirë se vini në Shqip Story Podcast. Sot dëgjojmë rrëfime autentike nga arkiva.\n02:00 [Sponsor Break] Ky episod mbështetet nga partnerët tanë zyrtarë. Përdorni kodin SHQIP për ulje speciale.\n03:30 [Mprehësi] Dëshmitari i parë tregon se si ngjarja filloi gjatë vitit 1998 në rajonin verior.\n10:00 [Sponsor Break] Reklamë e shkurtër nga sponsori yne i dytë BetterHelp terapi në internet."
            ),
            EpisodeEntity(
                id = "ep_harbinger_1",
                podcastId = "pod_harbinger_1344999619",
                podcastTitle = "The Jordan Harbinger Show",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/ce/57/a9/ce57a912-523d-5e81-f461-c71591eb2b4b/mza_855972047978822038.jpeg/600x600bb.jpg",
                title = "Deconstructing Manipulation Tactics & Psychological Influence",
                description = "Jordan sits down with former behavioral analysts to dissect deception detection, social engineering techniques, and how to safeguard your personal boundaries in high-stakes environments.",
                durationSeconds = 2100, // 35 mins
                publishDate = "2026-09-02",
                audioUrl = realAudioUrls[3],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "180,840,1500",
                chapters = "0:Welcome & Guest Intro|180:Sponsor: BetterHelp|270:Micro-Expressions and Verbal Cues|540:The Anatomy of Social Engineering|840:Sponsor: Shopify|930:Emotional Anchoring Techniques|1500:Sponsor: SimpliSafe|1590:Building Psychological Resilience|1950:Jordan's Final Thoughts",
                transcript = "0:00 [Jordan] Welcome to the Jordan Harbinger Show. Today we're deconstructing deception detection tactics.\n03:00 [Sponsor Break] This episode is brought to you by BetterHelp online therapy. Visit betterhelp.com/jordan for 10% off.\n04:30 [Guest] When people lie under stress, micro-expressions reveal hidden emotional state.\n14:00 [Sponsor Break] Sponsored by Shopify. Build your online business today for just \$1 per month at shopify.com/jordan.\n25:00 [Sponsor Break] Supported by SimpliSafe home security systems. Protect your home with 24/7 monitoring."
            ),
            EpisodeEntity(
                id = "ep_aom_1",
                podcastId = "pod_aom_332516054",
                podcastTitle = "The Art of Manliness",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/ec/db/72/ecdb72bd-11e5-5c9b-87a6-a8f157b214db/mza_11005987414919781126.jpeg/600x600bb.jpg",
                title = "The Philosophy of Stoic Resilience in the Modern World",
                description = "Brett McKay explores how Seneca, Epictetus, and Marcus Aurelius approached adversity, mental fortitude, and disciplined living amidst unpredictable times.",
                durationSeconds = 1920, // 32 mins
                publishDate = "2026-09-01",
                audioUrl = realAudioUrls[4],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "240,900",
                chapters = "0:Introduction & The Dichotomy of Control|240:Sponsor: Huckberry|330:Meditations of Marcus Aurelius|660:Voluntary Discomfort as a Tool|900:Sponsor: Factor Meals|990:The View From Above & Perspective|1560:Practical Daily Stoic Habits|1800:Wrap-up",
                transcript = "0:00 [Brett] Welcome back to the Art of Manliness podcast. Today we discuss stoic resilience.\n04:00 [Sponsor Break] Brought to you by Huckberry. Exceptional outdoor gear, boots, and clothing. Use code AOM20.\n05:30 [Brett] Epictetus famously wrote that we control our intentions and actions, but not external events.\n15:00 [Sponsor Break] Sponsored by Factor Meals. Fresh, chef-crafted meals delivered right to your doorstep."
            ),
            EpisodeEntity(
                id = "ep_peterson_1",
                podcastId = "pod_peterson_1184022695",
                podcastTitle = "The Jordan B. Peterson Podcast",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/58/16/42/581642ef-7f31-d538-7c69-0a42ec25c604/mza_5721699391369703653.jpeg/600x600bb.jpg",
                title = "The Architecture of Meaning & Psychological Responsibility",
                description = "Dr. Jordan B. Peterson explores maps of meaning, the mythological archetype of the hero, and why bearing voluntary responsibility creates sustainable purpose in life.",
                durationSeconds = 2700, // 45 mins
                publishDate = "2026-08-31",
                audioUrl = realAudioUrls[5],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "300,1200",
                chapters = "0:Introduction & Genesis of Purpose|300:Sponsor: DailyWire+|390:Chaos, Order, and the Sacred Border|840:The Hero's Journey Across Cultures|1200:Sponsor: ExpressVPN|1290:Voluntary Confrontation with Adversity|2100:The Role of Art & Conscience|2550:Closing Reflections",
                transcript = "0:00 [Dr. Peterson] Hello everyone. Today's discussion focuses on voluntary responsibility.\n05:00 [Sponsor Break] This episode is brought to you by DailyWire+. Access exclusive documentaries and news.\n06:30 [Dr. Peterson] When you adopt responsibility for your life and community, meaning emerges naturally.\n20:00 [Sponsor Break] Sponsored by ExpressVPN. Protect your online data and private internet browsing."
            ),
            EpisodeEntity(
                id = "ep_batman_1",
                podcastId = "pod_batman_1802737962",
                podcastTitle = "DC High Volume: Batman",
                podcastCoverUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/17/2c/78/172c787f-79ce-80e7-cec2-2f08d6f1cbb1/mza_17006185782027313788.jpeg/600x600bb.jpg",
                title = "Episode 1: The Midnight Signal over Arkham",
                description = "As a torrential storm batters Gotham City, an encrypted emergency broadcast triggers alarms across Wayne Manor. Batman investigates a series of coordinated escapes deep beneath Arkham Asylum.",
                durationSeconds = 1500, // 25 mins
                publishDate = "2026-09-04",
                audioUrl = realAudioUrls[0],
                isDownloaded = false,
                playbackPositionMs = 0,
                adTimestampsSeconds = "90,720",
                chapters = "0:Gotham City Siren & Storm|90:Sponsor: DC Universe Infinite|180:Descent into Arkham Lower Ward|450:Encounter with Scarecrow's Toxin|720:Ad Break: Batman Graphic Novels|810:The Batmobile Pursuit through Burnside|1200:The Riddler's Cryptic Warning|1410:To Be Continued...",
                transcript = "0:00 [Narrator] Lightning illuminates the dark skyline of Gotham City as sirens echo over Wayne Manor.\n01:30 [Sponsor Break] Brought to you by DC Universe Infinite. Read over 25,000 digital comics.\n03:00 [Batman] Alfred, scan the Arkham perimeter. We have multiple perimeter breaches on sub-level 4.\n12:00 [Sponsor Break] Special ad break for Batman Year One hardcover graphic novel collection."
            )
        )

        podcastDao.insertPodcasts(defaultPodcasts)
        podcastDao.insertEpisodes(defaultEpisodes)

        // Clean up legacy dummy URLs if present
        cleanUpLegacyDummyData()

        podcastDao.insertSyncLog(SyncLogEntity(deviceName = "System", action = "Loaded default podcasts with real podcast audio streams: Huberman Lab, Shqip Story, Harbinger, Art of Manliness, Peterson, Batman"))
    }

    private suspend fun cleanUpLegacyDummyData() {
        val allEps = podcastDao.getAllEpisodes().first()
        val dummyUrls = listOf("soundhelix.com", "example.com")
        val replacementUrls = listOf(
            "https://traffic.megaphone.fm/SCIM7156610982.mp3",
            "https://traffic.megaphone.fm/SCIM7393383815.mp3",
            "https://traffic.megaphone.fm/SCIM2465421786.mp3",
            "https://traffic.megaphone.fm/SCIM3386045656.mp3"
        )
        for ((idx, ep) in allEps.withIndex()) {
            if (dummyUrls.any { ep.audioUrl.contains(it, ignoreCase = true) }) {
                val realUrl = replacementUrls[idx % replacementUrls.size]
                val updated = ep.copy(audioUrl = realUrl)
                podcastDao.updateEpisode(updated)
            }
        }
    }
}
