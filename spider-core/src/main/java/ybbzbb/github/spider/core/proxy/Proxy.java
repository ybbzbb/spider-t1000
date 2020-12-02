package ybbzbb.github.spider.core.proxy;

import lombok.*;
import ybbzbb.github.spider.core.util.GsonUtil;

/**
 * 
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Proxy {

	private String host;
	private int port;
	private String username;
	private String password;

	private int rebootLimit;
	private int rebootCount;
	private int timeOutCount;
	private int userCount;

	public Proxy(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public Proxy(String host, int port , String username , String password) {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Proxy proxy = (Proxy) o;

		if (port != proxy.port) return false;
		if (host != null ? !host.equals(proxy.host) : proxy.host != null) return false;
		if (username != null ? !username.equals(proxy.username) : proxy.username != null) return false;
		return password != null ? password.equals(proxy.password) : proxy.password == null;
	}

	@Override
	public int hashCode() {
		int result = host != null ? host.hashCode() : 0;
		result = 31 * result + port;
		result = 31 * result + (username != null ? username.hashCode() : 0);
		result = 31 * result + (password != null ? password.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return GsonUtil.toJson(this);
	}
}
