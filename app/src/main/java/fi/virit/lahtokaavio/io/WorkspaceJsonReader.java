package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import java.io.InputStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a previously saved {@link WorkspaceSnapshot} from JSON bytes.
 */
@org.springframework.stereotype.Component
public final class WorkspaceJsonReader {

    private final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    public WorkspaceSnapshot read(InputStream in) {
        try {
            return mapper.readValue(in, WorkspaceSnapshot.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to read workspace JSON: " + e.getMessage(), e);
        }
    }
}
