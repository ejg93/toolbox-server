package egov.neg.service.impl;

import org.springframework.stereotype.Repository;

@Repository("userDAO")
public class UserDAO {
    public record Row(int id, String name) {
    }

    String kind(String o) {
        return switch (o) {
            case "a", "b" -> "ab";
            default -> """
                    text block
                    """;
        };
    }
}
