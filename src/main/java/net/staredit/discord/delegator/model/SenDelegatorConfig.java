package net.staredit.discord.delegator.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SenDelegatorConfig {

    private String senDelegatorToken;
    private ServerConfig defaultServerConfig;
    private List<ServerConfig> serverConfig;
    private transient Map<String, ServerConfig> guildIdToServerConfig;

    @Override
    public String toString() {
        return "SenDelegatorConfig{" +
                "senDelegatorToken='" + senDelegatorToken + '\'' +
                ", defaultServerConfig=" + defaultServerConfig +
                ", serverConfig=" + serverConfig +
                ", guildIdToServerConfig=" + guildIdToServerConfig +
                '}';
    }

}
