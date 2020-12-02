package ybbzbb.github.spider.core.util.http;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class Zipper {

    public static GZIPOutputStream enZip(ByteArrayOutputStream data){
        try(GZIPOutputStream zipStream =
                    new GZIPOutputStream(data)
        ){
            return zipStream;
        }catch (Exception e){
            log.error("zipper enZip error " + e.getMessage());
        }

        return null;
    }

    public static ByteArrayOutputStream deZip(){
        return null;
    }
}
