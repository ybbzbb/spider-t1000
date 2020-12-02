package ybbzbb.github.spider.core.model;

import lombok.*;

import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CookieAO implements Serializable {
    private String name;
    private String value;
}
