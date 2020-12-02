package ybbzbb.github.spider.core.util.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;

import java.io.*;
import java.util.ArrayList;

/**
 * @author RAY
 * @creed: http  文件解析
 * @date
 */
@Slf4j
public class HttpAnalysis {

    private static final LineParser lineParser = BasicLineParser.INSTANCE;

    public static InputStream buildHttpFile(HttpResponse httpResponse){
        if (httpResponse == null) {
            return null;
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        StringBuffer sb = new StringBuffer();
        sb.append(httpResponse.getStatusLine()).append("\r");

        for (Header header : httpResponse.getAllHeaders()) {
            sb.append(header.toString()).append("\r");
        }

        sb.append("\r");
        HttpEntity entity = httpResponse.getEntity();

        for (byte aByte : sb.toString().getBytes()) {
            output.write(aByte);
        }

        try (InputStream is = entity.getContent()){
            int inByte;
            while((inByte = is.read()) != -1)
                output.write(inByte);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(output.toByteArray());
    }


    public static HttpResponse buildResponse(ByteArrayOutputStream out){

        ArrayList<String> lines = new ArrayList<>();

        try(BufferedReader bufferedReader
                    = new BufferedReader(
                            new StringReader(
                                    new String(out.toByteArray()
                                    )))){
            String line;
            while ((line = bufferedReader.readLine()) != null){
                if (line.equals("\r")) {
                    break;
                }
                lines.add(line);
            }
        }catch (Exception e){
            log.error("buildResponse error " + e.getMessage() );
        }

        final StatusLine statusline = BasicLineParser.parseStatusLine(lines.get(0) , lineParser);
        lines.remove(0);


        HttpResponse response = new BasicHttpResponse(statusline);


        return null;
    }

}
