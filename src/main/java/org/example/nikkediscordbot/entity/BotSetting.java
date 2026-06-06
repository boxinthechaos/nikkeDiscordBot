package org.example.nikkediscordbot.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bot_settings")
public class BotSetting {

    @Id
    private String discordId;

    private String channelId;

    public BotSetting() {
    }

    public BotSetting(String guildId, String channelId) {
        this.discordId = guildId;
        this.channelId = channelId;
    }

    public String getDiscordId() {
        return discordId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }
}