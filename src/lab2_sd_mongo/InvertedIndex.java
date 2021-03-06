/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lab2_sd_mongo;
 
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
 
public class InvertedIndex {
    
    //static Mongo mongo = new Mongo("localhost",27017);
    
    static ArrayList<String> palabras = ProcesaXML.palabras;

    public static List<String> stopwords;

    static Map<String, List<Tuple3>> index = new HashMap<>();
    static List<String> files = new ArrayList<>();

    public static void indexFile(String titulo, String cuerpo) throws IOException {

        // estructurra indice
        // (key,value) -> (palabra, [( documentoID, ocurrencias  ),...] )

        try (FileReader fr = new FileReader("stop-words-spanish.txt")) {//<editor-fold defaultstate="collapsed" desc="comment">

            if(fr == null){
                System.out.println("Archivo erroneo");
                System.exit(1);
            }
            try (BufferedReader bf = new BufferedReader(fr)) {
                palabras = new ArrayList();
                String aux1;

                while( (aux1 = bf.readLine() ) != null ){
                    palabras.add(aux1);
                }
            }
        }
 //</editor-fold>

        int fileno = files.indexOf(titulo);
        if (fileno == -1) {
                files.add(titulo);
                fileno = files.size() - 1;
        }

        int pos = 0;

        StringTokenizer st = new StringTokenizer(cuerpo);
        // iterate through st object to get more tokens from it
        while (st.hasMoreElements()) {
            String token = st.nextElement().toString();
            String word = token.toLowerCase();
            pos++;
            if (stopwords.contains(word))
                    continue;
            // cargo (palabra, [( doc, ocurrencias  ),...] )
            List<Tuple3> idx = index.get(word);
            // si no existe en el indice la palabra, creo una lista de 
            // values(doc, ocurrencias) i agrego el doc,ocurrencia
            if (idx == null) {
                idx = new LinkedList<>();
                index.put(word, idx);
                idx.add(new Tuple3(files.get(fileno), fileno, 1));
            }
            // Si ya existe la palabra en el indice
            // busco el documento en los values asociados
            else{
                // si ya existe el documento en el indice
                // incremento las ocurrencias de la palabra
                boolean flag = false;
                for (int i = 0; i < idx.size(); i++) {
                    if( idx.get(i).fileno ==  fileno){
                        idx.get(i).count++;
                        flag = true;
                    }
                }
                // si no existe este documento en el indice
                // agrego documento contando una ocurrencia
                if(flag == false){
                    idx.add(new Tuple3(files.get(fileno), fileno, 1));
                    flag = true;
                }
            }
        }

        System.out.println("indexed " + titulo + " " + pos + " words");
    }

    public static ArrayList<String> search(ArrayList<String> words, DBCollection index_db, DBCollection coleccion){
//        List<Tuple3> answer = new LinkedList<>();
        List<Tuple3> respuesta = new LinkedList<>();
        
        BasicDBObject inQuery = new BasicDBObject();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            list.add(words.get(i));
        }
        
        List<List<String>> list_part= new ArrayList<>();
        for (int i = 0; i < LAB2_SD_MONGO.particiones; i++) {
            list_part.add(new ArrayList<>());
        }
        for (int i = 0; i < words.size(); i++) {
            list_part.get(hash(words.get(i), LAB2_SD_MONGO.particiones)).add(words.get(i));
        }
        System.out.println(list_part);
        
        
        for (int k = 0; k < LAB2_SD_MONGO.particiones; k++) {
            DBCollection rename = LAB2_SD_MONGO.db.getCollection("shard"+Integer.toString(k));
            inQuery.put("key", new BasicDBObject("$in", list_part.get(k)));
            DBCursor cursor = rename.find(inQuery);

            while(cursor.hasNext()) {
                DBObject obj = cursor.next();
                String asd = obj.get("values").toString();

                String findStr = "docID";
                int lastIndex = 0, count = 0;

                while(lastIndex != -1){
                    lastIndex = asd.indexOf(findStr,lastIndex);
                    if(lastIndex != -1){
                        count ++;
                        lastIndex += findStr.length();
                    }
                }

                System.out.println("count: " + count);

                List<List<Object>> response = new ArrayList<>();

                String xtr = asd.replaceAll("[a-zA-Z\\[\\]\\{\\}\":, ]", " ");
                xtr = xtr.replaceAll("\\s+", " ");
                String[] items = xtr.split(" ");

                int[] results = new int[items.length];
                for (int i = 0; i < items.length; i++) {
                    try {
                        results[i] = Integer.parseInt(items[i]);
                    } catch (NumberFormatException nfe) {

                    }
                }            
                for (int i = 0; i < count; i++) {
                    response.add(new ArrayList<>());
                    for (int j = 0; j < 2; j++) {
                        response.get(i).add(results[i*2+j+1]);
                    }
                }

                for (int i = 0; i < count; i++) {                   
                    DBCursor cur = coleccion.find();
                    cur.skip((int) response.get(i).get(0));
                    cur.next();
                    DBObject doc= cur.curr();

                    boolean presente = false;
                    for (int j = 0; j < respuesta.size(); j++) {                    
                        // si la estoy mostrando
                        if(respuesta.get(j).titulo.equals(doc.get("titulo"))){
                            // sumo la cantidad de ocurrencias(por cada palabra de la query)
                            // a.k.a. query = "hoy mismo"
                            // -> hoy   -> doc3, 4 ocurrencias
                            // -> mismo -> doc3, 2 ocurrencias
                            // answer -> doc3, (4+2) ocurrencias
                            // asi rankeo las respuestas
                            respuesta.get(j).count = respuesta.get(j).count + (int) response.get(i).get(1);
                            presente = true;
                        }
                    }
                    // si no la estoy mostrando en mi respuesta
                    if(presente == false){
                        // la agrego
                        respuesta.add(new Tuple3(doc.get("titulo").toString(), (int) response.get(i).get(0), (int) response.get(i).get(1) ));  
                        presente = true;
                    }
                }
            }
        }
        
//        inQuery.put("key", new BasicDBObject("$in", list));
//        System.out.println("XXXXXX " + inQuery);
//        DBCursor cursor = index_db.find(inQuery);
//
//        while(cursor.hasNext()) {
//            DBObject obj = cursor.next();
//            String asd = obj.get("values").toString();
//            
//            String findStr = "docID";
//            int lastIndex = 0, count = 0;
//
//            while(lastIndex != -1){
//                lastIndex = asd.indexOf(findStr,lastIndex);
//                if(lastIndex != -1){
//                    count ++;
//                    lastIndex += findStr.length();
//                }
//            }
//            
//            System.out.println("count: " + count);
//            
//            List<List<Object>> response = new ArrayList<>();
//            
//            String xtr = asd.replaceAll("[a-zA-Z\\[\\]\\{\\}\":, ]", " ");
//            xtr = xtr.replaceAll("\\s+", " ");
//            String[] items = xtr.split(" ");
//            
//            int[] results = new int[items.length];
//            for (int i = 0; i < items.length; i++) {
//                try {
//                    results[i] = Integer.parseInt(items[i]);
//                } catch (NumberFormatException nfe) {
//                
//                }
//            }            
//            for (int i = 0; i < count; i++) {
//                response.add(new ArrayList<>());
//                for (int j = 0; j < 2; j++) {
//                    response.get(i).add(results[i*2+j+1]);
//                }
//            }
//            
//            for (int i = 0; i < count; i++) {                   
//                DBCursor cur = coleccion.find();
//                cur.skip((int) response.get(i).get(0));
//                cur.next();
//                DBObject doc= cur.curr();
//                
//                boolean presente = false;
//                for (int j = 0; j < respuesta.size(); j++) {                    
//                    // si la estoy mostrando
//                    if(respuesta.get(j).titulo.equals(doc.get("titulo"))){
//                        // sumo la cantidad de ocurrencias(por cada palabra de la query)
//                        // a.k.a. query = "hoy mismo"
//                        // -> hoy   -> doc3, 4 ocurrencias
//                        // -> mismo -> doc3, 2 ocurrencias
//                        // answer -> doc3, (4+2) ocurrencias
//                        // asi rankeo las respuestas
//                        respuesta.get(j).count = respuesta.get(j).count + (int) response.get(i).get(1);
//                        presente = true;
//                    }
//                }
//                // si no la estoy mostrando en mi respuesta
//                if(presente == false){
//                    // la agrego
//                    respuesta.add(new Tuple3(doc.get("titulo").toString(), (int) response.get(i).get(0), (int) response.get(i).get(1) ));  
//                    presente = true;
//                }
//            }
//        }
        
        // ordenamos de mayor a menor cantidad de ocurrencias las respuestas
        Collections.sort(respuesta, new Tuple3Comparator() );
        Collections.reverse(respuesta);
        
        ArrayList<String> toIndex = new ArrayList();

        System.out.print("Busqueda: " + words + "\nResultados: ");
        respuesta.stream().map((f) -> {
            DBCursor cur = coleccion.find();
            cur.skip(f.fileno);
            cur.next();
            DBObject doc= cur.curr();
            toIndex.add(f.titulo);
            return f;            
        }).forEach((f) -> {
            System.out.print("\n   " + f.titulo + ", ocurrencias: " + f.count);
        });
        System.out.println("");        
        
        return toIndex;
        
        //<editor-fold defaultstate="collapsed" desc="comment">
//        for (int i=0; i<words.size(); i++) {
//            String word = words.get(i).toLowerCase();
//            // para cada palabra cargo sus values(doc, ocurrencias)
//            List<Tuple3> idx = index.get(word);
//            // si existen values
//            if (idx != null) {
//                // para cada tupla que existe
//                for (Tuple3 t : idx) {
//                    Tuple3 e = new Tuple3(files.get(t.fileno),(int)t.fileno ,(int)t.count);
//                    boolean presente = false;
//                    for(int j=0; j<answer.size(); j++){
//                        // si la estoy mostrando
//                        if ( answer.get(j).titulo.equals(e.titulo) ) {
//                            // sumo la cantidad de ocurrencias(por cada palabra de la query)
//                            // a.k.a. query = "hoy mismo"
//                            // -> hoy   -> doc3, 4 ocurrencias
//                            // -> mismo -> doc3, 2 ocurrencias
//                            // answer -> doc3, (4+2) ocurrencias
//                            // asi rankeo las respuestas
//                            answer.get(j).count = answer.get(j).count + e.count;
//                            presente = true;
//                        }
//                    }
//                    // si no la estoy mostrando en mi respuesta
//                    if(presente == false){
//                        // la agrego
//                        answer.add(e);
//                        presente = true;
//                    }
//                }
//            }
//        }
//</editor-fold>

//        // ordenamos de mayor a menor cantidad de ocurrencias las respuestas
//        Collections.sort(answer, new Tuple3Comparator() );
//        Collections.reverse(answer);
//
//        System.out.print("Busqueda: " + words + "\nResultados: ");
//        for (Tuple3 f : answer) {
//            
//            DBCursor cur = coleccion.find();
//            cur.skip(f.fileno);
//            cur.next();
//            DBObject doc= cur.curr();
//            
//            System.out.print("\n   " + f.titulo + ", ocurrencias: " + f.count);
//        }
//        System.out.println("");

    }

    public static void indexar() throws IOException {
        
        DBCursor cursor_remove = LAB2_SD_MONGO.index_db.find();
	while (cursor_remove.hasNext()) {
            LAB2_SD_MONGO.index_db.remove(cursor_remove.next());
	}
        
//        System.out.println("Index size: " + LAB2_SD_MONGO.index_db.count() + 
//                          " Db size: " + LAB2_SD_MONGO.coleccion.count() );
        
        try (FileReader fr = new FileReader("stop-words-spanish.txt")) {
            if(fr == null){
                System.out.println("Archivo erroneo");
                System.exit(1);
            }
            try (BufferedReader bf = new BufferedReader(fr)) {
                palabras = new ArrayList();
                String aux1;

                while( (aux1 = bf.readLine() ) != null ){
                    palabras.add(aux1);
                }
            }
        }

        stopwords = palabras;
        DBCursor cursor;
        cursor = LAB2_SD_MONGO.coleccion.find();

        while( cursor.hasNext() ){
            DBObject documento = cursor.next();
            String titulo = (String) documento.get("titulo");
            String cuerpo = (String) documento.get("cuerpo");

            indexFile(titulo, cuerpo);
        }
        
        // para cada entrada del index(java)
        Iterator<Map.Entry<String,List<Tuple3>>> itr1 = index.entrySet().iterator();
        while(itr1.hasNext()) {
            Map.Entry<String,List<Tuple3>> entry = itr1.next();
            List<Tuple3> values = index.get(entry.getKey());
            
            // se crea nueva entrada de index(mongoDB)
            BasicDBObject document = new BasicDBObject();
            // ingresamos la llave(la palabra)
            document.put("key"   , entry.getKey()   );
            
            
            // creamos la lista de [(docID, ocurrencias),...,] para cada palabra
            List<List<BasicDBObject>> contenido = new ArrayList<>();
            contenido.add(new ArrayList());
            for(int h=0; h<values.size(); h++){
                contenido.get(contenido.size()-1).add(new BasicDBObject());
                contenido.get(contenido.size()-1).get(contenido.get(contenido.size()-1).size()-1).put("docID", values.get(h).fileno);
                contenido.get(contenido.size()-1).get(contenido.get(contenido.size()-1).size()-1).put("ocurrencias", values.get(h).count);
            }
            // ingresamos los valores
            document.put("values", contenido);
            // insertamos al indice
            DBCollection rename = LAB2_SD_MONGO.db.createCollection("shard"+hash(entry.getKey(), LAB2_SD_MONGO.particiones), null);
            //DBCollection rename = LAB2_SD_MONGO.shard.rename("shard"+Integer.toString(9), true);
            LAB2_SD_MONGO.index_db.insert(document);
            rename.insert(document);
            
        }
        
//        System.out.println("Index size: " + LAB2_SD_MONGO.index_db.count() + 
//                          " Db size: " + LAB2_SD_MONGO.coleccion.count() );
//        
    }

    static class Tuple3 {
        String titulo;
        int fileno;
        int count;

        public Tuple3(String titulo, int fileno, int count) {
            this.titulo = titulo;
            this.fileno = fileno;
            this.count = count;
        }
    }

    private class Tuple {
        private int fileno;
        private int count;

        public Tuple(int fileno, int count) {
            this.fileno = fileno;
            this.count = count;
        }
    }
    
    static class Tuple3Comparator implements Comparator<Tuple3> {
        @Override
        public int compare(Tuple3 t0, Tuple3 t1) {
            return Integer.valueOf(t0.count).compareTo(t1.count);
        }
    }
    
    private static int hash(String id, int size) {
        int hash = 13;
        for (int i = 0; i < id.length(); i++) {
            hash = hash*31 + id.charAt(i);
        }
        // Nos aseguramos de que sea positivo
        hash = (int) Math.sqrt(hash*hash);
        // Determinamos la particion a ocupar
        hash = hash%size;
        return hash;
    }
}