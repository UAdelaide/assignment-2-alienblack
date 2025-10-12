import java.util.*;
import java.util.stream.Collectors;

/**
 * Interlocking implementation (single file).
 * Matches the user's path set:
 *  NS: 1-5-8, 1-5-9, 3-4, 3-7-11
 *  SN: 4-3, 9-6-2, 10-6-2, 11-7-3
 * Enforces:
 *  - Atomic per-cycle movement
 *  - Passenger-first at the diamond (1->5, 2->6 vs 3<->4)
 *  - No mixing passenger/freight subnets
 *  - Legal entry/exit only
 *  - Prevents head-on swaps & same-destination double-lands
 */
public class InterlockingImpl implements Interlocking {

    /* ---------- tiny Pair ---------- */
    static final class Pair<U, V> {
        final U first; final V second;
        private Pair(U a, V b){ this.first=a; this.second=b; }
        static <U,V> Pair<U,V> of(U a, V b){ return new Pair<>(a,b); }
        @Override public boolean equals(Object o){
            if (this==o) return true;
            if (!(o instanceof Pair)) return false;
            Pair<?,?> p=(Pair<?,?>)o;
            return Objects.equals(first,p.first) && Objects.equals(second,p.second);
        }
        @Override public int hashCode(){ return Objects.hash(first,second); }
    }

    /* ---------- sections ---------- */
    static final class TrackSection {
        final int id;
        Train train; boolean occupied;
        TrackSection(int id){ this.id=id; }
        boolean isOccupied(){ return occupied; }
        void addTrain(Train t){
            if (occupied && this.train!=t) throw new IllegalStateException("Track "+id+" occupied");
            this.train=t; occupied=true;
        }
        void moveTrain(){ // advance and clear
            if (train!=null){ train.advance(); train=null; occupied=false; }
        }
        String getTrainName(){ return train==null ? null : train.name; }
    }

    /* ---------- topology ---------- */
    static final class Topology {
        enum Line { PAX, FREIGHT }

        // entries/exits exactly as brief
        static final Set<Integer> ENTRY_S = Set.of(1,3);             // southbound enter
        static final Set<Integer> ENTRY_N = Set.of(4,9,10,11);       // northbound enter
        static final Set<Integer> EXIT_S  = Set.of(4,8,9,11);        // southbound exits
        static final Set<Integer> EXIT_N  = Set.of(2,3);             // northbound exits

        // line assignment (11 is FREIGHT)
        static final Map<Integer,Line> LINE = Map.ofEntries(
            Map.entry(1,Line.PAX), Map.entry(2,Line.PAX),
            Map.entry(5,Line.PAX), Map.entry(6,Line.PAX),
            Map.entry(8,Line.PAX), Map.entry(9,Line.PAX),
            Map.entry(10,Line.PAX),
            Map.entry(3,Line.FREIGHT), Map.entry(4,Line.FREIGHT),
            Map.entry(7,Line.FREIGHT), Map.entry(11,Line.FREIGHT)
        );

        // undirected adjacency derived from the user's legal path set
        static final Map<Integer,Set<Integer>> ADJ;
        static {
            Map<Integer,Set<Integer>> a = new HashMap<>();
            a.put(1,  Set.of(5));
            a.put(5,  Set.of(1,8,9));
            a.put(8,  Set.of(5));
            a.put(9,  Set.of(5,6));
            a.put(6,  Set.of(9,10,2));
            a.put(10, Set.of(6));
            a.put(2,  Set.of(6));
            a.put(3,  Set.of(4,7));
            a.put(4,  Set.of(3));
            a.put(7,  Set.of(3,11));
            a.put(11, Set.of(7));
            ADJ = Collections.unmodifiableMap(a);
        }

        static boolean adjacent(int a,int b){ return ADJ.getOrDefault(a,Set.of()).contains(b); }
        static boolean sameLine(int a,int b){ return LINE.get(a)==LINE.get(b); }

        // only these are the diamond
        static boolean isDiamondEdge(int from,int to){
            return (from==1 && to==5) || (from==2 && to==6) || (from==3 && to==4) || (from==4 && to==3);
        }
        static boolean isPassenger(int s){ return LINE.get(s)==Line.PAX; }
        static boolean isFreight(int s){ return LINE.get(s)==Line.FREIGHT; }

        static boolean isSouthDest(int d){ return EXIT_S.contains(d); }
        static boolean isNorthDest(int d){ return EXIT_N.contains(d); }
    }

    /* ---------- trains ---------- */
    static final class Train {
        final String name; final List<Integer> path; int idx=0;
        Train(String name,List<Integer> path){ this.name=name; this.path=path; }
        int current(){ return idx<path.size()? path.get(idx) : -1; }
        int next(){ int n=idx+1; return (n<path.size()? path.get(n) : -1); } // -1 => exit
        void advance(){ idx++; }
    }

    /* ---------- move intent ---------- */
    static final class Intent {
        final Train t; final int from; final int to; // to==-1 => exit
        Intent(Train t,int f,int to){ this.t=t; this.from=f; this.to=to; }
    }

    /* ---------- state ---------- */
    private final Map<Integer,TrackSection> sections = new HashMap<>();
    private final Map<String,Train> trains = new HashMap<>();

    public InterlockingImpl(){ for(int i=1;i<=11;i++) sections.put(i,new TrackSection(i)); }

    /* BFS inside one line */
    private List<Integer> bfsPath(int start,int dest){
        if (start==dest) return List.of(start);
        Map<Integer,Integer> parent=new HashMap<>();
        ArrayDeque<Integer> q=new ArrayDeque<>();
        parent.put(start,-1); q.add(start);
        while(!q.isEmpty()){
            int u=q.poll();
            for(int v: Topology.ADJ.getOrDefault(u,Set.of())){
                if (!Topology.sameLine(u,v)) continue;
                if (parent.containsKey(v)) continue;
                parent.put(v,u); q.add(v);
                if (v==dest){
                    ArrayDeque<Integer> rev=new ArrayDeque<>();
                    for(int x=v;x!=-1;x=parent.get(x)) rev.addFirst(x);
                    return new ArrayList<>(rev);
                }
            }
        }
        throw new IllegalArgumentException("No route from "+start+" to "+dest);
    }

    @Override
    public void addTrain(String name,int entry,int dest){
        boolean south=Topology.isSouthDest(dest), north=Topology.isNorthDest(dest);
        if(!south && !north) throw new IllegalArgumentException("Illegal destination: "+dest);
        if(south && !Topology.ENTRY_S.contains(entry))
            throw new IllegalArgumentException("Illegal southbound entry: "+entry);
        if(north && !Topology.ENTRY_N.contains(entry))
            throw new IllegalArgumentException("Illegal northbound entry: "+entry);
        if(!Topology.sameLine(entry,dest))
            throw new IllegalArgumentException("Entry/destination on different lines");

        TrackSection e=sections.get(entry);
        if(e==null) throw new IllegalArgumentException("Unknown section "+entry);
        if(e.isOccupied()) throw new IllegalStateException("Entry section "+entry+" occupied");
        if(trains.containsKey(name)) throw new IllegalArgumentException("Train name in use: "+name);

        List<Integer> path=bfsPath(entry,dest);
        Train t=new Train(name,path);
        e.addTrain(t);
        trains.put(name,t);
    }

    @Override
    public int moveTrains(String[] names){
        // snapshot occupancy at cycle start
        Map<Integer,String> occ=new HashMap<>();
        for(var e: sections.entrySet())
            if(e.getValue().isOccupied()) occ.put(e.getKey(), e.getValue().train.name);

        // collect intents (don’t mutate yet)
        List<Intent> intents=new ArrayList<>();
        for(String n: names){
            Train t=trains.get(n);
            if(t==null) throw new IllegalArgumentException("Train "+n+" is not in service");
            int from=t.current(), to=t.next(); // -1 => exit
            if(to!=-1){
                if(!Topology.adjacent(from,to)) continue;
                if(!Topology.sameLine(from,to)) continue;
                if(occ.containsKey(to)) continue;
            }
            intents.add(new Intent(t,from,to));
        }

        // diamond vs non-diamond
        List<Intent> diamond=new ArrayList<>(), other=new ArrayList<>();
        for(Intent in: intents){
            if(in.to!=-1 && Topology.isDiamondEdge(in.from,in.to)) diamond.add(in);
            else other.add(in);
        }

        // passenger-first at diamond
        boolean paxReq = diamond.stream().anyMatch(in -> Topology.isPassenger(in.from));
        if(paxReq) diamond.removeIf(in -> Topology.isFreight(in.from));
        // mutex: at most one diamond move per cycle
        if(diamond.size()>1){
            diamond.sort(Comparator.comparingInt(in -> in.from)); // deterministic
            diamond = List.of(diamond.get(0));
        }

        // prevent head-on swaps & same-destination double-lands in 'other'
        Map<Pair<Integer,Integer>,Intent> byEdge=new HashMap<>();
        Set<Intent> losers=new HashSet<>();
        for(Intent in: other){
            if(in.to==-1) continue;
            Pair<Integer,Integer> e=Pair.of(in.from,in.to);
            Pair<Integer,Integer> r=Pair.of(in.to,in.from);
            byEdge.put(e,in);
            if(byEdge.containsKey(r)){ losers.add(in); losers.add(byEdge.get(r)); }
        }
        Map<Integer,Intent> winByDest=new HashMap<>();
        for(Intent in: other){
            if(losers.contains(in)) continue;
            if(in.to==-1) continue;
            winByDest.merge(in.to,in,(a,b)-> a.from<=b.from ? a : b);
        }

        // winners: diamond + exits + dest winners
        List<Intent> winners=new ArrayList<>();
        winners.addAll(diamond);
        winners.addAll(other.stream().filter(in -> in.to==-1 && !losers.contains(in)).collect(Collectors.toList()));
        winners.addAll(new HashSet<>(winByDest.values()));

        // commit atomically
        for(Intent in: winners){
            sections.get(in.from).moveTrain();
            if(in.to!=-1) sections.get(in.to).addTrain(in.t);
            else trains.remove(in.t.name);
        }
        return winners.size();
    }

    @Override
    public String getSection(int sectionId){
        TrackSection s=sections.get(sectionId);
        if(s==null) throw new IllegalArgumentException("Track section does not exist: "+sectionId);
        return s.getTrainName();
    }

    @Override
    public int getTrain(String trainName){
        Train t=trains.get(trainName);
        if(t==null) throw new IllegalArgumentException("Train name does not exist");
        return t.current();
    }

    public static void removeTrain(String trainName){ /* no-op for compatibility */ }
}
