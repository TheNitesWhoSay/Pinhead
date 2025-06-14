package net.staredit.discord.delegator;

public class SecurityKey {

    String getKey() {
        return new StringBuilder()
                .append("Place key here") // TODO: Add random key before compiling releases containing encrypted info
                .toString();
    }
}
