package edu.umd.cloud9.exercise.pagerank;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;

import edu.uci.ics.jung.algorithms.cluster.WeakComponentGraphClusterer;
import edu.uci.ics.jung.algorithms.importance.PageRank;
import edu.uci.ics.jung.algorithms.importance.Ranking;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;

public class SequentialPageRank {

	public static void main(String[] args) throws IOException {
		if ( args.length != 1 ) {
			System.err.println("usage: SequentialPageRage [graph-adjacency-list]");
			System.exit(-1);
		}
		String infile = args[0];

		int edgeCnt = 0;
		DirectedSparseGraph<String, Integer> graph = new DirectedSparseGraph<String, Integer>();

		// read in raw text records, line separated
		BufferedReader data = new BufferedReader(new InputStreamReader(
				new FileInputStream(infile)));

		String line;
		while ((line = data.readLine()) != null) {
			line.trim();
			String[] arr = line.split("\\t");

			//System.out.print(arr[0] + " ->");
			for (int i = 1; i < arr.length; i++) {
				graph.addEdge(new Integer(edgeCnt++), arr[0], arr[i]);
				//System.out.print(" " + arr[i]);
			}
			//System.out.print("\n");
		}

		data.close();

		WeakComponentGraphClusterer<String, Integer> clusterer = new WeakComponentGraphClusterer<String, Integer>();

		Collection<Graph<String, Integer>> components = clusterer
				.transform(graph);
		int numComponents = components.size();
		System.out.println("Number of components: " + numComponents);

		PageRank<String, Integer> ranker = new PageRank<String, Integer>(graph,
				0.15);
		ranker.evaluate();

		for (Ranking<?> s : (List<Ranking<?>>) ranker.getRankings()) {
			String pmid = s.getRanked().toString();

			System.out.println(pmid + " " + s.rankScore);
		}
	}

}
