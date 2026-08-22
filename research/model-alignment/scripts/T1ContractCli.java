import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small offline CLI used by the cross-language T1 parity audit. */
public final class T1ContractCli {

    private T1ContractCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: T1ContractCli <raw-json-file> <cause-count> <check-count>");
        }
        String raw = Files.readString(Path.of(args[0]));
        var result = T1Contract.score(raw, Integer.parseInt(args[1]), Integer.parseInt(args[2]), null, null, null);
        System.out.println(new ObjectMapper().writeValueAsString(result));
    }
}
