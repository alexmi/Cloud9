/*
 * Cloud9: A MapReduce Library for Hadoop
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package edu.umd.cloud9.collection.wikipedia;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

/**
 * Tool for taking a Wikipedia XML dump file and spits out articles in a flat text file (article
 * title and content, separated by a tab).
 *
 * @author Jimmy Lin
 * @author Peter Exner
 */
public class DumpWikipediaToPlainText extends Configured implements Tool {
  private static final Logger LOG = Logger.getLogger(DumpWikipediaToPlainText.class);

  private static enum PageTypes {
    TOTAL, REDIRECT, DISAMBIGUATION, EMPTY, ARTICLE, STUB, FEATURED, GOOD, OTHER
  };

  private static class MyMapper extends Mapper<LongWritable, WikipediaPage, Text, Text> {
    private static final Text articleName = new Text();
    private static final Text articleContent = new Text();

    @Override
    public void map(LongWritable key, WikipediaPage p, Context context)
        throws IOException, InterruptedException {
      Boolean skip = false;
      String limit = context.getConfiguration().get("limit.retrieval");

      context.getCounter(PageTypes.TOTAL).increment(1);

      if (p.isRedirect()) {
        context.getCounter(PageTypes.REDIRECT).increment(1);
      } else if (p.isDisambiguation()) {
        context.getCounter(PageTypes.DISAMBIGUATION).increment(1);
      } else if (p.isEmpty()) {
        context.getCounter(PageTypes.EMPTY).increment(1);
      } else if (p.isArticle()) {
        context.getCounter(PageTypes.ARTICLE).increment(1);

        if (p.isStub()) {
          context.getCounter(PageTypes.STUB).increment(1);
        }

        if (p.isFeaturedArticle()) {
          context.getCounter(PageTypes.FEATURED).increment(1);
        }
        else if (limit.equals("featured")) {
          skip = true;
        }

        if (p.isGoodArticle()) {
          context.getCounter(PageTypes.GOOD).increment(1);
        }
        else if (limit.equals("good")) {
          skip = true;
        }

        if ( !skip) {
          articleName.set(p.getTitle().replaceAll("[\\r\\n]+", " "));
          articleContent.set(p.getContent().replaceAll("[\\r\\n]+", " "));
          context.write(articleName, articleContent);
        }

      } else {
        context.getCounter(PageTypes.OTHER).increment(1);
      }
    }
  }

  private static final String INPUT_OPTION = "input";
  private static final String OUTPUT_OPTION = "output";
  private static final String LANGUAGE_OPTION = "wiki_language";
  private static final String LIMIT_OPTION = "limit_retrieval";

  @SuppressWarnings("static-access")
  @Override
  public int run(String[] args) throws Exception {
    Options options = new Options();
    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("XML dump file").create(INPUT_OPTION));
    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("output path").create(OUTPUT_OPTION));
    options.addOption(OptionBuilder.withArgName("en|sv|de|cs|es|zh|ar|tr").hasArg()
        .withDescription("two-letter language code").create(LANGUAGE_OPTION));
    options.addOption(OptionBuilder.withArgName("featured|good").hasArg()
        .withDescription("type of article to retrieve").create(LIMIT_OPTION));

    CommandLine cmdline;
    CommandLineParser parser = new GnuParser();
    try {
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println("Error parsing command line: " + exp.getMessage());
      return -1;
    }

    if (!cmdline.hasOption(INPUT_OPTION) || !cmdline.hasOption(OUTPUT_OPTION)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(this.getClass().getName(), options);
      ToolRunner.printGenericCommandUsage(System.out);
      return -1;
    }

    String language = "en"; // Assume "en" by default.
    if (cmdline.hasOption(LANGUAGE_OPTION)) {
      language = cmdline.getOptionValue(LANGUAGE_OPTION);
      if (language.length() != 2) {
        System.err.println("Error: \"" + language + "\" unknown language!");
        return -1;
      }
    }

    String inputPath = cmdline.getOptionValue(INPUT_OPTION);
    String outputPath = cmdline.getOptionValue(OUTPUT_OPTION);

    String limit = "none"; // Assume all articles by default.
    if (cmdline.hasOption(LIMIT_OPTION)) {
      limit = cmdline.getOptionValue(LIMIT_OPTION);
      if (limit.equals("good") && limit.equals("featured")) {
        System.err.println("Error: Unknown limit = \"" + limit + "\"!");
        return -1;
      }
    }

    LOG.info("Tool name: " + this.getClass().getName());
    LOG.info(" - XML dump file: " + inputPath);
    LOG.info(" - output path: " + outputPath);
    LOG.info(" - language: " + language);
    LOG.info(" - limit: " + limit);

    Job job = Job.getInstance(getConf());
    job.setJarByClass(DumpWikipediaToPlainText.class);
    job.setJobName(String.format("DumpWikipediaToPlainText[%s: %s, %s: %s, %s: %s, %s: %s]", INPUT_OPTION,
        inputPath, OUTPUT_OPTION, outputPath, LANGUAGE_OPTION, language, INPUT_OPTION, limit));

    job.setNumReduceTasks(0);

    FileInputFormat.setInputPaths(job, new Path(inputPath));
    FileOutputFormat.setOutputPath(job, new Path(outputPath));

    if (language != null) {
      job.getConfiguration().set("wiki.language", language);
    }

    job.setInputFormatClass(WikipediaPageInputFormat.class);
    job.setOutputFormatClass(TextOutputFormat.class);
    job.getConfiguration().set("limit.retrieval", limit);

    job.setMapperClass(MyMapper.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);

    // Delete the output directory if it exists already.
    FileSystem.get(getConf()).delete(new Path(outputPath), true);

    job.waitForCompletion(true);

    return 0;
  }

  public DumpWikipediaToPlainText() {
  }

  public static void main(String[] args) throws Exception {
    ToolRunner.run(new DumpWikipediaToPlainText(), args);
  }
}
