package net.staredit.discord.delegator.model;

import lombok.Data;

@Data
public class ServerConfig {

    private String serverId;
    private String serverName;
    private String notificationChannelId;
    private String notificationChannelName;
    private Boolean sendWelcomeMessage;

}
