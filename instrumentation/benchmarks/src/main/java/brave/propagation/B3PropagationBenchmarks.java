package brave.propagation;

import brave.internal.HexCodec;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class B3PropagationBenchmarks {
  static final Propagation<String> b3 = Propagation.B3_STRING;
  static final Injector<Map<String, String>> b3Injector = b3.injector(Map::put);
  static final Extractor<Map<String, String>> b3Extractor = b3.extractor(Map::get);
  static final MutableTraceContext.Extractor<Map<String, String>> b3MutableExtractor =
      B3Propagation.FACTORY.extractor(Map::get);

  static final TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
      .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
      .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .sampled(true)
      .build();

  static final Map<String, String> incoming = new LinkedHashMap<String, String>() {
    {
      b3Injector.inject(context, this);
    }
  };

  static final Map<String, String> incomingNotSampled = new LinkedHashMap<String, String>() {
    {
      put("X-B3-Sampled", "0"); // unsampled
    }
  };

  static final Map<String, String> incomingMalformed = new LinkedHashMap<String, String>() {
    {
      put("x-amzn-trace-id", "Sampled=-;Parent=463ac35%Af6413ad;Root=1-??-abc!#%0123456789123456");
      put("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124"); // ok
      put("X-B3-SpanId", "48485a3953bb6124"); // ok
      put("X-B3-ParentSpanId", "-"); // not ok
    }
  };

  static final Map<String, String> nothingIncoming = Collections.emptyMap();

  @Benchmark public void inject() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3Injector.inject(context, carrier);
  }

  @Benchmark public TraceContextOrSamplingFlags extract() {
    return b3Extractor.extract(incoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_nothing() {
    return b3Extractor.extract(nothingIncoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_unsampled() {
    return b3Extractor.extract(incomingNotSampled);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_malformed() {
    return b3Extractor.extract(incomingMalformed);
  }

  @Benchmark public void mutable_extract() {
    b3MutableExtractor.extract(incoming, new MutableTraceContext());
  }

  @Benchmark public void mutable_extract_nothing() {
    b3MutableExtractor.extract(nothingIncoming, new MutableTraceContext());
  }

  @Benchmark public void mutable_extract_unsampled() {
    b3MutableExtractor.extract(incomingNotSampled, new MutableTraceContext());
  }

  @Benchmark public void mutable_extract_malformed() {
    b3MutableExtractor.extract(incomingMalformed, new MutableTraceContext());
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + B3PropagationBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}