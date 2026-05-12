package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises a {@link WorkspaceSnapshot} to indented JSON bytes — pretty
 * enough for a human to skim, compact enough that the file stays small.
 */
@org.springframework.stereotype.Component
public final class WorkspaceJsonWriter {

    private final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public byte[] writeBytes(WorkspaceSnapshot snapshot) {
        return mapper.writeValueAsBytes(snapshot);
    }
}
