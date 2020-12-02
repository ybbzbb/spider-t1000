package ybbzbb.github.spider.core.proxy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MyProxy extends Proxy {

    private int failNum;//失败次数
	private long rebootLimitTime;
	private long timeLimitTime;
	private boolean isReboot = false;
	private String hostKey;
	private long userTime;

}
