package com.example.ui

/**
 * Localization helper for Albanian (Shqip) and English/Default.
 */
object AppLanguage {

    fun getTabLabel(isAlbanian: Boolean, tab: PodcastViewModel.Tab): String = when (tab) {
        PodcastViewModel.Tab.DISCOVER -> if (isAlbanian) "Zbulo" else "Discover"
        PodcastViewModel.Tab.DOWNLOADS -> if (isAlbanian) "Offline" else "Offline"
        PodcastViewModel.Tab.VERLAUF -> if (isAlbanian) "Historiku" else "Verlauf"
        PodcastViewModel.Tab.SETTINGS -> if (isAlbanian) "Cilësimet" else "Settings"
    }

    fun searchPlaceholder(isAlbanian: Boolean): String =
        if (isAlbanian) "Kërko podkaste, autorë, kanale..." else "Search Apple Podcasts, Spotify, BBC, NPR..."

    fun searchTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Kërko podkaste" else "Search Podcasts"

    fun exitToast(isAlbanian: Boolean): String =
        if (isAlbanian) "Shtypni përsëri për të dalë nga aplikacioni" else "Press back again to exit"

    fun settingsTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Cilësimet" else "Einstellungen"

    fun settingsSubtitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Gjuha, pamja, kapërcimi i reklamave & përditësimet" else "Erscheinungsbild, GitHub Releases & CI/CD"

    fun languageSectionTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Gjuha / Language" else "Sprache / Language"

    fun albanianSwitchTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Gjuha Shqipe (Albanian)" else "Albanian Language (Gjuha Shqipe)"

    fun albanianSwitchSubtitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Ndërfaqja e aplikacionit është aktive në gjuhën shqipe" else "Aktivizo gjuhën shqipe në të gjithë aplikacionin"

    fun appearanceTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Pamja & Tema" else "Erscheinungsbild & Theme"

    fun appearanceSubtitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Zgjidhni midis temës së errët Obsidian dhe temës së çelët." else "Wähle zwischen dem immersiven Dark Theme und dem eleganten Light Theme."

    fun darkTheme(isAlbanian: Boolean): String =
        if (isAlbanian) "E errët" else "Dark Cyber"

    fun lightTheme(isAlbanian: Boolean): String =
        if (isAlbanian) "E çelët" else "Light Clean"

    fun systemTheme(isAlbanian: Boolean): String =
        if (isAlbanian) "Sistemi" else "System"

    fun smartAdSkipTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Kapërcyesi inteligjent i reklamave" else "Smart Ad & Wave Skipper"

    fun smartAdSkipSubtitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Kapërcen automatikisht segmentet e sponsorëve dhe ngritjet e valëve akustike." else "Erkennt und überspringt automatisch Sponsor-Segmente & Audio-Spikes."

    fun smartDownloadTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Shkarkime inteligjente" else "Smart Downloads"

    fun smartDownloadSubtitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Shkarko episodet automatikisht dhe fshi ato pas 2 ditësh." else "Lade Episoden im Hintergrund herunter und lösche sie nach 2 Tagen."

    fun offlineModeTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Modaliteti vetëm Offline" else "Reiner Offline-Modus"

    fun offlineModeSubtitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Bllokon të gjithë trafikun e rrjetit; luhen vetëm episodet e shkarkuara." else "Blockiert jeden Netzwerk-Traffic; spielt nur heruntergeladene Episoden."

    fun loggingTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Regjistrimi i ngjarjeve" else "Detailliertes Event-Logging"

    fun updateSectionTitle(isAlbanian: Boolean): String =
        if (isAlbanian) "Përditësimet GitHub OTA" else "GitHub Releases & In-App Updates"

    fun checkForUpdates(isAlbanian: Boolean): String =
        if (isAlbanian) "Kontrollo për përditësime" else "Auf Updates prüfen"

    fun chapters(isAlbanian: Boolean): String =
        if (isAlbanian) "Kapitujt e episodit" else "EPISODE CHAPTERS"

    fun transcript(isAlbanian: Boolean): String =
        if (isAlbanian) "Transkripti & Segmentet" else "TRANSCRIPT & AD DETECTOR"

    fun episodeNotes(isAlbanian: Boolean): String =
        if (isAlbanian) "Shënimet e episodit" else "EPISODE NOTES & DETAILS"

    fun sleepTimer(isAlbanian: Boolean): String =
        if (isAlbanian) "Kohëmatësi i gjumit" else "SLEEP TIMER / COUNTDOWN"

    fun zapAd(isAlbanian: Boolean): String =
        if (isAlbanian) "Kërce reklamën" else "Zap Ad"

    fun offlineMemory(isAlbanian: Boolean): String =
        if (isAlbanian) "Kujtesë Offline" else "Offline Memory"

    fun cloudStream(isAlbanian: Boolean): String =
        if (isAlbanian) "Transmetim nga Reja" else "Cloud Stream"
}
