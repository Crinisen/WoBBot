package wiresegal.wob

import de.btobastian.javacord.entities.message.Message
import de.btobastian.javacord.entities.message.embed.EmbedBuilder
import de.btobastian.javacord.entities.permissions.PermissionState
import de.btobastian.javacord.entities.permissions.PermissionType
import de.btobastian.javacord.entities.permissions.PermissionsBuilder
import de.btobastian.javacord.exceptions.DiscordException

/**
 * @author WireSegal
 * Created at 11:40 PM on 2/15/18.
 */

val API_URL_MATCHER = "^https://wob\\.coppermind\\.net/api/\\w+/(\\d+)".toRegex()

fun embedFromContent(titlePrefix: String, entry: Entry): EmbedBuilder {
    val date = entry.event.date.split("-")
    val month = months[date[1].toInt() - 1]
    val dateStr = "($month ${date[2]}, ${date[0]})"

    val title = titlePrefix + entry.event.name + " " + dateStr

    val embed = EmbedBuilder()
            .setColor(arcanumColor)
            .setTitle(title)
            .setUrl("https://wob.coppermind.net/events/${API_URL_MATCHER.find(entry.event.url)!!.groupValues[1]}/#e${API_URL_MATCHER.find(entry.url)!!.groupValues[1]}")
            .setThumbnail(iconUrl)

    when {
        entry.event.reviewState == ReviewState.PENDING ->
            embed.setDescription("__**Pending Review**__")
        entry.paraphrased ->
            embed.setDescription("__**Paraphrased**__")
        entry.event.reviewState == ReviewState.APPROVED ->
            embed.setDescription("**Approved**")
    }

    if (entry.note != null)
        embed.setFooter(entry.note)

    val arcanumSuffix = "*… (Check Arcanum for more.)*"
    for ((speaker, comment) in entry.lines.map {
                val speaker = it.speaker
                val comment = it.getTrueText()
                if (comment.length > 1024) speaker to comment.substring(0, 1024 - arcanumSuffix.length)
                        .replace("\\w+$".toRegex(), "").trim() + arcanumSuffix
                else speaker to comment
            }) {

        embed.addField(speaker, comment, false)
        val newJson = embed.toJsonNode()
        val footer = newJson.objectNode()
        footer.put("text", "(Too long to display. Check Arcanum for more.)")
        val size = footer.toString().length - newJson.get("footer").toString().length
        if (newJson.toString().length > 2000 - size) {
            newJson.set("footer", footer)
            return FakeEmbedBuilder(newJson)
        }
    }

    if (embed.toJsonNode().toString().length > 2000)
        return backupEmbed(title, entry)

    return embed
}

fun backupEmbed(title: String, entry: Entry): EmbedBuilder {
    val backup = EmbedBuilder().setColor(arcanumColor).setTitle(title)
            .setUrl("https://wob.coppermind.net/events/${API_URL_MATCHER.find(entry.event.url)!!.groupValues[1]}/#e${API_URL_MATCHER.find(entry.url)!!.groupValues[1]}")
            .setThumbnail(iconUrl)
    backup.setDescription("This WoB is too long. Click on the link above to see it on Arcanum.")
    return backup
}

val months = listOf("Jan.", "Feb.", "March", "April",
        "May", "June", "July", "Aug.",
        "Sept.", "Oct.", "Nov.", "Dec.")

fun harvestFromSearch(terms: List<String>): List<EmbedBuilder> {
    val allArticles = entriesFromSearch(terms)
    val allEmbeds = mutableListOf<EmbedBuilder>()

    val articles = if (allArticles.size > 250) allArticles.subList(0, 250) else allArticles

    val size = if (allArticles !== articles) "... (250)" else articles.size.toString()

    for ((idx, article) in allArticles.withIndex()) {
        val titleText = "Search: \"${terms.joinToString()}\" (${idx+1}/$size) \n"
        allEmbeds.add(embedFromContent(titleText, article))
    }

    return allEmbeds
}

fun search(message: Message, terms: List<String>) {
    async {
        val waiting = message.channel.sendMessage("Searching for \"${terms.joinToString().replace("&!", "!")}\"...").get()
        val type = message.channel.typeContinuously()
        val allEmbeds = harvestFromSearch(terms)
        type.close()

        try {
            when {
                allEmbeds.isEmpty() -> message.channel.sendMessage("Couldn't find any WoBs for \"${terms.joinToString().replace("&!", "!")}\".")
                allEmbeds.size == 1 -> {
                    val finalEmbed = allEmbeds.first()
                    finalEmbed.setTitle(finalEmbed.toJsonNode()["title"].asText().replace(".*\n".toRegex(), ""))
                    message.channel.sendMessage(finalEmbed).get().setupDeletable(message.author)
                }
                else ->
                    message.channel.sendMessage(allEmbeds.first()).get()
                            .setupDeletable(message.author).setupControls(message.author, 0, allEmbeds)
            }
            waiting.delete()
        } catch (e: DiscordException) {
            message.channel.sendMessage("An error occurred trying to look up the WoB.")
        }
    }
}

fun about(message: Message) {
    val invite = api.createBotInvite(PermissionsBuilder().setState(PermissionType.MANAGE_MESSAGES, PermissionState.ALLOWED).build())
    val wireID = 77084495118868480L
    val wire = api.getUserById(wireID)
    val wireStr = if (wire.isPresent) wire.get().mentionTag else "@wiresegal#1522"
    val host = api.owner
    val hostStr = if (host.isPresent) host.get().mentionTag else "@wiresegal#1522"

    val add = if (hostStr != wireStr) "\nHosted by: $hostStr" else ""

    message.channel.sendMessage(EmbedBuilder().apply {
        setTitle("About WoBBot")
        setColor(arcanumColor)

        setDescription("Commands: \n" +
                " - `!wob`\n" +
                " - `!wobrandom`\n" +
                " - `Ask the Silent Gatherers`\n" +
                " - `Consult the Diagram`\n" +
                " - `Check the Gemstone Archives`\n" +
                " - `!wobrank` (Admin only)\n" +
                "Author: $wireStr$add\n" +
                "[Invite Link]($invite) | " +
                "[Github Source](https://github.com/yrsegal/WoBBot) | " +
                "[Arcanum](https://wob.coppermind.net/)")
    })
}

