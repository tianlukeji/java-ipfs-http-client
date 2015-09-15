package org.ipfs;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

public class IPFS {

    public enum Command {
        add,
        block,
        bootstrap,
        cat,
        commands,
        config,
        dht,
        diag,
        dns,
        get,
        id,
        log,
        ls,
        mount,
        name,
        object,
        pin,
        ping,
        refs,
        repo,
        resolve,
        stats,
        swarm,
        tour,
        file,
        update,
        version,
        bitswap
    }

    public final String host;
    public final int port;
    private final String version;
    public final Pin pin = new Pin();
    public final Repo repo = new Repo();
    public final IPFSObject object = new IPFSObject();
    public final Swarm swarm = new Swarm();

    public IPFS(String host, int port) {
        this(host, port, "/api/v0/");
    }

    public IPFS(String host, int port, String version) {
        this.host = host;
        this.port = port;
        this.version = version;
    }

    public List<MerkleNode> add(List<NamedStreamable> files) throws IOException {
        Multipart m = new Multipart("http://" + host + ":" + port + version+"add?stream-channels=true", "UTF-8");
        for (NamedStreamable f : files)
            m.addFilePart("file", f);
        String res = m.finish();
        return JSONParser.parseStream(res).stream().map(x -> MerkleNode.fromJSON((Map<String, Object>) x)).collect(Collectors.toList());
    }

    public List<MerkleNode> ls(MerkleNode merkleObject) throws IOException {
        Map res = (Map) retrieveAndParse("ls/" + merkleObject.hash);
        return ((List<Object>) res.get("Objects")).stream().map(x -> MerkleNode.fromJSON((Map) x)).collect(Collectors.toList());
    }

    public byte[] cat(MerkleNode merkleObject) throws IOException {
        return retrieve("cat/" + merkleObject.hash);
    }

    // level 2 commands

    class Pin {
        public Object add(MerkleNode merkleObject) throws IOException {
            return retrieveAndParse("pin/add?stream-channels=true&arg=" + merkleObject.hash);
        }

        public Object ls() throws IOException {
            return retrieveAndParse("pin/ls?stream-channels=true");
        }

        public List<MerkleNode> rm(MerkleNode merkleObject, boolean recursive) throws IOException {
            Map json = (Map) retrieveAndParse("pin/rm?stream-channels=true&r=" + recursive + "&arg=" + merkleObject.hash);
            return ((List<Object>) json.get("Pinned")).stream().map(x -> new MerkleNode((String) x)).collect(Collectors.toList());
        }
    }

    class Repo {
        public Object gc() throws IOException {
            return retrieveAndParse("repo/gc");
        }
    }

    class IPFSObject {
        public List<MerkleNode> put(List<byte[]> data) throws IOException {
            Multipart m = new Multipart("http://" + host + ":" + port + version+"add?stream-channels=true", "UTF-8");
            for (byte[] f : data)
                m.addFilePart("file", new NamedStreamable.ByteArrayWrapper(f));
            String res = m.finish();
            return JSONParser.parseStream(res).stream().map(x -> MerkleNode.fromJSON((Map<String, Object>) x)).collect(Collectors.toList());
        }

        public MerkleNode get(MerkleNode merkleObject) throws IOException {
            Map json = (Map)retrieveAndParse("object/get?stream-channels=true&arg=" + merkleObject.hash);
            json.put("Hash", merkleObject.hash);
            return MerkleNode.fromJSON(json);
        }

        public MerkleNode links(MerkleNode merkleObject) throws IOException {
            Map json = (Map)retrieveAndParse("object/links?stream-channels=true&arg=" + merkleObject.hash);
            return MerkleNode.fromJSON(json);
        }

        public Map<String, Object> stat(MerkleNode merkleObject) throws IOException {
            return (Map)retrieveAndParse("object/stat?stream-channels=true&arg=" + merkleObject.hash);
        }

        public byte[] data(MerkleNode merkleObject) throws IOException {
            return retrieve("object/data?stream-channels=true&arg=" + merkleObject.hash);
        }

        // TODO new, patch
    }

    class Swarm {
        public List<NodeAddress> peers() throws IOException {
            Map m = (Map)retrieveAndParse("swarm/peers?stream-channels=true&arg=");
            return ((List<Object>)m.get("Strings")).stream().map(x -> new NodeAddress((String)x)).collect(Collectors.toList());
        }
    }

    public Object retrieveAndParse(String path) throws IOException {
        byte[] res = retrieve(path);
        return JSONParser.parse(new String(res));
    }

    public byte[] retrieve(String path) throws IOException {
        URL target = new URL("http", host, port, version + path);
        return IPFS.get(target);
    }

    public Object retrieveAndParsePost(String path, byte[] body) throws IOException {
        URL target = new URL("http", host, port, version + path);
        byte[] res = post(target, body, Collections.EMPTY_MAP);
        return JSONParser.parse(new String(res));
    }

    public static byte[] get(URL target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");

        InputStream in = conn.getInputStream();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();

        byte[] buf = new byte[4096];
        int r;
        while ((r=in.read(buf)) >= 0)
            resp.write(buf, 0, r);
        return resp.toByteArray();
    }

    public static byte[] post(URL target, byte[] body, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        for (String key: headers.keySet())
            conn.setRequestProperty(key, headers.get(key));
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        OutputStream out = conn.getOutputStream();
        out.write(body);
        out.flush();
        out.close();

        InputStream in = conn.getInputStream();
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r=in.read(buf)) >= 0)
            resp.write(buf, 0, r);
        return resp.toByteArray();
    }
}
