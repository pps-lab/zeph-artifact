package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

public class TextWidget {

	private String title;
	private String text;
	private String moreInfo;
	private String auth_token;

	public TextWidget(String title, String text, String moreInfo, String auth_token) {
		this.title = title;
		this.text = text;
		this.moreInfo = moreInfo;
		this.auth_token = auth_token;
	}

	public String getAuth_token() {
		return auth_token;
	}

	public void setAuth_token(String auth_token) {
		this.auth_token = auth_token;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getMoreInfo() {
		return moreInfo;
	}

	public void setMoreInfo(String moreInfo) {
		this.moreInfo = moreInfo;
	}

	@Override
	public String toString() {
		return "TextWidget [title=" + title + ", text=" + text + ", moreInfo=" + moreInfo + ", auth_token=" + auth_token
				+ "]";
	}

}
