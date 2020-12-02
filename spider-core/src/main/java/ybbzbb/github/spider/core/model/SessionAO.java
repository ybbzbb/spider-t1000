package ybbzbb.github.spider.core.model;


import lombok.*;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SessionAO implements Serializable {

    private String site;

    private List<CookieAO> cookieAOS;

    private String key;

    private String host;

    private int port;

    private String username;

    private String password;

    private String userAgent;

    private boolean isOut = true;
}
