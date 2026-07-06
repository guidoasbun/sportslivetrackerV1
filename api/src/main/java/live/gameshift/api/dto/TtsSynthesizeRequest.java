package live.gameshift.api.dto;

public record TtsSynthesizeRequest(
        String text,
        String voiceId
) {}
