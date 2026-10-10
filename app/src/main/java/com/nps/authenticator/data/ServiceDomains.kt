package com.nps.authenticator.data

/** Associe un nom de service (issuer) à son domaine, pour retrouver son icône. */
object ServiceDomains {
    private val known = mapOf(
        "google" to "google.com", "gmail" to "google.com", "github" to "github.com",
        "gitlab" to "gitlab.com", "bitbucket" to "bitbucket.org", "netlify" to "netlify.com",
        "vercel" to "vercel.com", "heroku" to "heroku.com", "cloudflare" to "cloudflare.com",
        "digitalocean" to "digitalocean.com", "amazon" to "amazon.com", "aws" to "aws.amazon.com",
        "microsoft" to "microsoft.com", "outlook" to "outlook.com", "apple" to "apple.com",
        "facebook" to "facebook.com", "meta" to "meta.com", "instagram" to "instagram.com",
        "twitter" to "x.com", "x" to "x.com", "linkedin" to "linkedin.com", "reddit" to "reddit.com",
        "discord" to "discord.com", "slack" to "slack.com", "telegram" to "telegram.org",
        "whatsapp" to "whatsapp.com", "tiktok" to "tiktok.com", "snapchat" to "snapchat.com",
        "twitch" to "twitch.tv", "steam" to "steampowered.com", "epic games" to "epicgames.com",
        "dropbox" to "dropbox.com", "notion" to "notion.so", "figma" to "figma.com",
        "atlassian" to "atlassian.com", "zoom" to "zoom.us", "paypal" to "paypal.com",
        "stripe" to "stripe.com", "binance" to "binance.com", "coinbase" to "coinbase.com",
        "kraken" to "kraken.com", "proton" to "proton.me", "protonmail" to "proton.me",
        "yahoo" to "yahoo.com", "ebay" to "ebay.com", "shopify" to "shopify.com",
        "wordpress" to "wordpress.com", "npm" to "npmjs.com", "docker" to "docker.com",
        "mongodb" to "mongodb.com", "supabase" to "supabase.com", "firebase" to "firebase.google.com",
        "openai" to "openai.com", "anthropic" to "anthropic.com", "ovh" to "ovh.com",
        "namecheap" to "namecheap.com", "godaddy" to "godaddy.com",
    )

    private val DOMAIN =
        Regex("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}$")

    /** Nettoie une saisie (« https://www.Netlify.com/x » -> « netlify.com »). Retourne "" si invalide. */
    fun clean(input: String): String {
        var s = input.trim().lowercase()
        s = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        return if (DOMAIN.matches(s)) s else ""
    }

    /** Domaine déduit du nom du service : liste connue, ou nom qui ressemble déjà à un domaine. */
    fun guess(issuer: String): String {
        val key = issuer.trim().lowercase()
        known[key]?.let { return it }
        return clean(key).takeIf { it.contains('.') && !key.contains(' ') } ?: ""
    }
}
