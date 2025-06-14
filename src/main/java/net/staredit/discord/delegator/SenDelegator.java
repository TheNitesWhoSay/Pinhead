package net.staredit.discord.delegator;

import com.google.gson.Gson;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.staredit.discord.delegator.model.SenDelegatorConfig;
import net.staredit.discord.delegator.model.ServerConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class SenDelegator extends ListenerAdapter
{
    private static final Logger logger = LoggerFactory.getLogger(SenDelegator.class);
    private static final SenDelegatorConfig config;
    private static final Map<String, ServerConfig> guildIdToConfig;

    private JDA jda = null;
    private List<Guild> guilds = new ArrayList<>();
    private Map<String, Set<String>> userPinnedMessages = new HashMap<>();

    public static ServerConfig prepareDefaultServerConfig(ServerConfig defaultServerConfig) {
        if ( defaultServerConfig == null ) {
            defaultServerConfig = new ServerConfig();
        }

        // Null out server-specific identifiers
        defaultServerConfig.setServerId(null);
        defaultServerConfig.setServerName(null);
        defaultServerConfig.setServerId(null);
        defaultServerConfig.setNotificationChannelName(null);

        // If non server-specific identifier is null, set it to default value
        if ( defaultServerConfig.getSendWelcomeMessage() == null ) defaultServerConfig.setSendWelcomeMessage(true);

        return defaultServerConfig;
    }

    public static ServerConfig prepareConfig(ServerConfig config, ServerConfig defaultServerConfig) {
        if ( defaultServerConfig == null ) {
            defaultServerConfig = prepareDefaultServerConfig(null);
        }

        if ( config == null ) {
            config = new ServerConfig();
        }
        if ( config.getSendWelcomeMessage() == null ) {
            config.setSendWelcomeMessage(defaultServerConfig.getSendWelcomeMessage());
        }
        return config;
    }

    public static Map<String, ServerConfig> getServerIdToConfig(@NotNull SenDelegatorConfig config) {
        Map<String, ServerConfig> serverIdToConfig = new HashMap<>();

        ServerConfig defaultServerConfig = prepareDefaultServerConfig(config.getDefaultServerConfig());

        List<ServerConfig> serverConfigs = config.getServerConfig();
        if ( serverConfigs != null ) {
            for ( ServerConfig serverConfig : serverConfigs ) {
                if ( serverConfig != null ) {
                    serverConfig = prepareConfig(serverConfig, defaultServerConfig);
                    String serverId = serverConfig.getServerId();
                    if ( serverId != null && !serverId.isEmpty() ) {
                        if ( !serverIdToConfig.containsKey(serverId) ) {
                            serverIdToConfig.put(serverId, serverConfig);
                        } else {
                            logger.error("Duplicate serverId: {}", serverId);
                        }
                    } else {
                        logger.error("Null or empty serverId!");
                    }
                } else {
                    logger.error("Null serverConfig!");
                }
            }
        }
        return serverIdToConfig;
    }

    public static String getTokenFromSelf() {
        try {
            return SecurityCryptor.decrypt(SecurityCryptor.getResourceAsString("secure/data"));
        } catch ( Exception e ) {
            return null;
        }
    }

    public static SenDelegatorConfig getConfig() {
        try {
            String configContents = new String(Files.readAllBytes(
                    Paths.get("config", "sen-delegator-config.json")
            ), StandardCharsets.UTF_8);
            if ( configContents == null || configContents.isEmpty() ) {
                throw new RuntimeException("configContents was null!");
            }
            SenDelegatorConfig config = new Gson().fromJson(configContents, SenDelegatorConfig.class);
            if ( config == null ) {
                throw new RuntimeException("config was null!");
            }
            String token = config.getSenDelegatorToken();
            if ( token == null || token.isEmpty() ) {
                token = System.getenv("SEN_DELEGATOR_TOKEN");
                if ( token == null || token.isEmpty() ) {
                    token = getTokenFromSelf();
                    if ( token == null || token.isEmpty() )
                        throw new RuntimeException("senDelegatorToken must be defined!");
                }
                config.setSenDelegatorToken(token);
            }
            config.setDefaultServerConfig(prepareDefaultServerConfig(config.getDefaultServerConfig()));
            config.setGuildIdToServerConfig(getServerIdToConfig(config));
            return config;
        } catch ( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    static {
        config = getConfig();
        guildIdToConfig = config.getGuildIdToServerConfig();
        logger.info("Loaded up config: " + config);
    }

    public static void main( String[] args ) throws LoginException {
        JDA jda = JDABuilder.createDefault(config.getSenDelegatorToken())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT).build();
        jda.addEventListener(new net.staredit.discord.delegator.SenDelegator());
    }

    public SenDelegator() {
        super();
    }

    @Override
    public void onReady(@Nonnull ReadyEvent readyEvent) {
        jda = readyEvent.getJDA();
        logger.info("Started up and connected to discord successfully!");
    }

    @Override
    public void onGuildReady(@Nonnull GuildReadyEvent event) {
        if ( jda == null )
            jda = event.getJDA();

        Guild guild = event.getGuild();
        String guildId = guild.getId();
        logger.info("Loading guild: " + guild.getName());
        ServerConfig serverConfig = guildIdToConfig.get(guild.getId());
        if ( serverConfig != null ) {
            synchronized ( guilds ) {
                guilds.add(guild);
            }
            if ( serverConfig.getSendWelcomeMessage() != null && serverConfig.getSendWelcomeMessage().booleanValue() ) {
                logger.info(serverConfig.getNotificationChannelId());
                guild.getTextChannelById(serverConfig.getNotificationChannelId()).sendMessage(
                        "Reporting for duty.").queue(
                                (message -> {}), (ex -> logger.error("Exception sending message ", ex)));
            }
            Member member = guild.getSelfMember();
            EnumSet<Permission> permissions = member.getPermissions();
            boolean hasViewChannel = permissions.contains(Permission.VIEW_CHANNEL);
            boolean hasModerateMembers = permissions.contains(Permission.MODERATE_MEMBERS);
            boolean hasSendMessages = permissions.contains(Permission.MESSAGE_SEND);
            boolean hasManageMessages = permissions.contains(Permission.MESSAGE_MANAGE);
            boolean hasReadMessageHistory = permissions.contains(Permission.MESSAGE_HISTORY);
            logger.info("    In " + guild.getName() + " I have permissions..." +
                    "\n        VIEW_CHANNEL: " + (hasViewChannel ? "yes" : "no") +
                    "\n        MODERATE_MEMBERS: " + (hasModerateMembers ? "yes" : "no") +
                    "\n        MESSAGE_SEND: " + (hasSendMessages ? "yes" : "no") +
                    "\n        MESSAGE_MANAGE: " + (hasManageMessages ? "yes" : "no") +
                    "\n        MESSAGE_HISTORY: " + (hasReadMessageHistory ? "yes" : "no"));

            CommandListUpdateAction commands = jda.updateCommands();
            commands.addCommands(
                    Commands.context(Command.Type.MESSAGE, "Pin Message"),
                    Commands.context(Command.Type.MESSAGE, "Unpin Message"),
                    Commands.context(Command.Type.USER, "Give Role")
            );
            commands.queue();
        }
    }

    public boolean isModeratorOrPinHead(Member member) {
        if ( member == null ) {
            return false;
        } else if ( member.hasPermission(Permission.MODERATE_MEMBERS) ) {
            return true;
        } else {
            for ( Role role : member.getRoles() ) {
                if ( role.getName().equalsIgnoreCase("pin-head") ) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void onMessageContextInteraction(@Nonnull MessageContextInteractionEvent event) {
        if ( !event.isFromGuild() )
            return;

        if ( event.getCommandString().equals("Pin Message") ) {
            Member member = event.getMember();
            if ( !isModeratorOrPinHead(member) ) {
                event.reply("Insufficient privileges to use pin message!").queue();
                return;
            }
            Message targetMessage = event.getTarget();

            if ( targetMessage.isPinned() ) {
                event.reply("Message was already pinned!").queue();
            } else {
                Set<String> pinnedMessages = userPinnedMessages.get(event.getUser().getId());
                if ( pinnedMessages == null ) {
                    pinnedMessages = new HashSet<>();
                    userPinnedMessages.put(event.getUser().getId(), pinnedMessages);
                }
                pinnedMessages.add(targetMessage.getId());
                targetMessage.pin().queue();
                event.reply(event.getUser().getAsMention() + " pinned message " +
                        targetMessage.getJumpUrl() + " authored by " + targetMessage.getAuthor().getAsMention()).queue();
            }
        } else if ( event.getCommandString().equals("Unpin Message") ) {
            Member member = event.getMember();
            if ( !isModeratorOrPinHead(member) ) {
                event.reply("Insufficient privileges to use unpin message!").queue();
                return;
            }

            Message targetMessage = event.getTarget();
            if ( targetMessage.isPinned() ) {
                boolean isModerator = member != null && member.hasPermission(Permission.MODERATE_MEMBERS);

                Set<String> pinnedMessages = userPinnedMessages.get(event.getUser().getId());
                boolean canUnpin = isModerator || targetMessage.getAuthor().getId().equals(event.getUser().getId()) ||
                        (pinnedMessages != null && pinnedMessages.contains(targetMessage.getId()));

                if ( canUnpin ) {
                    targetMessage.unpin().queue();
                    if ( pinnedMessages != null ) {
                        pinnedMessages.remove(targetMessage.getId());
                    }
                    event.reply(event.getUser().getAsMention() + " unpinned message " + targetMessage.getJumpUrl()
                            + " authored by " + targetMessage.getAuthor().getAsMention()).queue();
                } else {
                    event.reply("You may only unpin your own messages or messages you've recently pinned." +
                            " This message must be unpinned by a moderator.").queue();
                }
            } else {
                event.reply("Message was already not-pinned!").queue();
            }
        }
    }

    @Override
    public void onUserContextInteraction(@Nonnull UserContextInteractionEvent event) {
        if ( !event.isFromGuild() )
            return;

        if ( event.getCommandString().equals("Give Role") ) {
            Member member = event.getMember();
            if ( !isModeratorOrPinHead(member) ) {
                event.reply("Insufficient privileges to use give role!").queue();
                return;
            }

            Member targetMember = event.getTargetMember();
            if ( targetMember != null ) {
                boolean hasWhatever = false;
                for ( Role role : targetMember.getRoles() ) {
                    if ( role.getName().equalsIgnoreCase("w/e") ) {
                        hasWhatever = true;
                        break;
                    }
                }
                if ( hasWhatever ) {
                    event.reply("Member " + targetMember.getAsMention() + " already has the w/e role").queue();
                } else {
                    List<Role> roles = targetMember.getGuild().getRolesByName("w/e", true);
                    if ( !roles.isEmpty() && roles.get(0).getName().equalsIgnoreCase("w/e") ) {
                        targetMember.getGuild().addRoleToMember(targetMember, roles.get(0)).queue();
                        event.reply(event.getUser().getAsMention() + " granted a role to " +
                                targetMember.getAsMention()).queue();
                    } else {
                        event.reply("ERROR: could not find the role to give!").queue();
                    }
                }
            } else {
                event.reply("ERROR: could not find the member!").queue();
            }
        }
    }

    public void onAdminMessage(@Nonnull MessageReceivedEvent e) {
        String rawMessage = e.getMessage().getContentRaw();
        if ( rawMessage.startsWith("!ping") ) {
            e.getMessage().getGuildChannel().sendMessage("pong!").complete();
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent e) {
        try {
            if ( e.isFromGuild() ) {
                String messageGuildId = e.getGuild().getId();
                ServerConfig serverConfig = guildIdToConfig.get(messageGuildId);
                if ( serverConfig != null ) {
                    User user = e.getAuthor();
                    if ( user.getIdLong() == 321087667968409601L ) {
                        onAdminMessage(e);
                    }
                }
            } else {
                logger.info("[Non-guild message] " + e.getAuthor().getName()
                        + ": " + e.getMessage().getContentDisplay());
            }
        } catch ( Exception ex ) {
            logger.error("onMessageReceived exception ", ex);
        }
    }
}
