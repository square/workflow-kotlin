// Appended to every generated karma.conf.js by KotlinMultiPlatformConventionPlugin.
//
// The Kotlin Gradle plugin's Karma reporter (kotlin-web-helpers/dist/karma-kotlin-reporter.js)
// reports each test back to Gradle by writing a "##teamcity[...]" service message to stdout, but it
// writes the messages back-to-back with no newline between them. Gradle's side of that protocol
// (TCServiceMessageOutputStreamHandler) buffers stdout until it sees a newline and gives up on any
// "line" longer than 1 MiB, logging "too long teamcity service message ... Event was lost". With
// tens of thousands of tests in a module the whole run is a single line, so most of the results are
// thrown away and Gradle records only a small, arbitrary subset of the tests that actually ran,
// including missing failures. Wrapping the reporter so that every message ends with a newline gives
// the parser the one-message-per-line stream it expects.
(function (config) {
  const KOTLIN_REPORTER_MODULE = 'kotlin-web-helpers/dist/karma-kotlin-reporter.js';
  const REPORTER_KEY = 'reporter:karma-kotlin-reporter';
  const KotlinReporter = require(KOTLIN_REPORTER_MODULE)[REPORTER_KEY][1];

  function NewlineDelimitedKotlinReporter(baseReporterDecorator, karmaConfig, emitter) {
    KotlinReporter.call(this, baseReporterDecorator, karmaConfig, emitter);
    const write = this.write;
    this.write = function (message) {
      return write.call(this, message + '\n');
    };
  }
  NewlineDelimitedKotlinReporter.$inject = KotlinReporter.$inject;

  // Replace the plugin registration made by the generated config rather than adding a second one.
  config.plugins = config.plugins.filter((plugin) => plugin !== KOTLIN_REPORTER_MODULE);
  config.plugins.push({ [REPORTER_KEY]: ['type', NewlineDelimitedKotlinReporter] });
})(config);
