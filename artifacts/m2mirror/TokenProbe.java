import org.antlr.runtime.Token;
public class TokenProbe {
  private final Token token;
  public TokenProbe(Token token) { this.token = token; }
  public Token getToken() { return token; }
}
