/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.demo;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jline.builtins.Builtins;
import org.jline.builtins.CommandRegistry;
import org.jline.builtins.Completers;
import org.jline.builtins.ConsoleEngine;
import org.jline.builtins.ConsoleEngineImpl;
import org.jline.builtins.Options;
import org.jline.builtins.SystemRegistryImpl;
import org.jline.builtins.Completers.OptDesc;
import org.jline.builtins.Completers.OptionCompleter;
import org.jline.builtins.Completers.SystemCompleter;
import org.jline.builtins.Options.HelpException;
import org.jline.builtins.Widgets.CmdDesc;
import org.jline.builtins.Widgets.TailTipWidgets;
import org.jline.builtins.Widgets.TailTipWidgets.TipType;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.ParsedLine;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.LineReader.Option;
import org.jline.reader.Parser.ParseContext;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.DefaultParser.Bracket;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.script.GroovyEngine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.InfoCmp.Capability;

/**
 * Demo how to create REPL app with JLine.
 *
 * @author <a href="mailto:matti.rintanikkola@gmail.com">Matti Rinta-Nikkola</a>
 */
public class Repl {

    private static class MyCommands implements CommandRegistry {
        private LineReader reader;
        private final Map<String,Builtins.CommandMethods> commandExecute = new HashMap<>();
        private Map<String,String> aliasCommand = new HashMap<>();
        private Exception exception;

        public MyCommands() {
            commandExecute.put("tput", new Builtins.CommandMethods(this::tput, this::tputCompleter));
            commandExecute.put("testkey", new Builtins.CommandMethods(this::testkey, this::defaultCompleter));
            commandExecute.put("clear", new Builtins.CommandMethods(this::clear, this::defaultCompleter));
        }

        public void setLineReader(LineReader reader) {
            this.reader = reader;
        }

        private Terminal terminal() {
            return reader.getTerminal();
        }

        public Set<String> commandNames() {
            return commandExecute.keySet();
        }

        public Map<String, String> commandAliases() {
            return aliasCommand;
        }

        public List<String> commandInfo(String command) {
            try {
                execute(command, new String[] {"--help"});
            } catch (HelpException e) {
                return Builtins.compileCommandInfo(e.getMessage());
            } catch (Exception e) {

            }
            return new ArrayList<>();
        }

        public boolean hasCommand(String command) {
            if (commandExecute.containsKey(command) || aliasCommand.containsKey(command)) {
                return true;
            }
            return false;
        }

        private String command(String name) {
            if (commandExecute.containsKey(name)) {
                return name;
            } else if (aliasCommand.containsKey(name)) {
                return aliasCommand.get(name);
            }
            return null;
        }

        public Completers.SystemCompleter compileCompleters() {
            SystemCompleter out = new SystemCompleter();
            for (String c : commandExecute.keySet()) {
                out.add(c, commandExecute.get(c).compileCompleter().apply(c));
            }
            out.addAliases(aliasCommand);
            return out;
        }

        public Object execute(String command, String[] args) throws Exception {
            exception = null;
            commandExecute.get(command(command)).execute().accept(new Builtins.CommandInput(args));
            if (exception != null) {
                throw exception;
            }
            return null;
        }

        public CmdDesc commandDescription(String command) {
            try {
                execute(command, new String[] {"--help"});
            } catch (HelpException e) {
                return Builtins.compileCommandDescription(e.getMessage());
            } catch (Exception e) {

            }
            return null;
        }

        private List<OptDesc> commandOptions(String command) {
            try {
                execute(command, new String[] {"--help"});
            } catch (HelpException e) {
                return Builtins.compileCommandOptions(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private void tput(Builtins.CommandInput input) {
            final String[] usage = {
                    "tput -  put terminal capability",
                    "Usage: tput [CAPABILITY]",
                    "  -? --help                       Displays command help"
            };
            Options opt = Options.compile(usage).parse(input.args());
            if (opt.isSet("help")) {
                exception = new HelpException(opt.usage());
                return;
            }

            List<String> argv = opt.args();
            try {
                if (argv.size() == 1) {
                    Capability vcap = Capability.byName(argv.get(0));
                    if (vcap != null) {
                        terminal().puts(vcap);
                    } else {
                        terminal().writer().println("Unknown capability");
                    }
                } else {
                    terminal().writer().println("Usage: tput [CAPABILITY]");
                }
            } catch (Exception e) {
                exception = e;
            }
        }

        private void testkey(Builtins.CommandInput input) {
            final String[] usage = {
                    "testkey -  display the key events",
                    "Usage: testkey",
                    "  -? --help                       Displays command help"
            };
            Options opt = Options.compile(usage).parse(input.args());
            if (opt.isSet("help")) {
                exception = new HelpException(opt.usage());
                return;
            }
            try {
                terminal().writer().write("Input the key event(Enter to complete): ");
                terminal().writer().flush();
                StringBuilder sb = new StringBuilder();
                while (true) {
                    int c = ((LineReaderImpl) reader).readCharacter();
                    if (c == 10 || c == 13) break;
                    sb.append(new String(Character.toChars(c)));
                }
                terminal().writer().println(KeyMap.display(sb.toString()));
                terminal().writer().flush();
            } catch (Exception e) {
                exception = e;
            }
        }

        private void clear(Builtins.CommandInput input) {
            final String[] usage = {
                    "clear -  clear terminal",
                    "Usage: clear",
                    "  -? --help                       Displays command help"
            };
            Options opt = Options.compile(usage).parse(input.args());
            if (opt.isSet("help")) {
                exception = new HelpException(opt.usage());
                return;
            }
            try {
                terminal().puts(Capability.clear_screen);
                terminal().flush();
            } catch (Exception e) {
                exception = e;
            }
        }

        private List<Completer> defaultCompleter(String command) {
            List<Completer> completers = new ArrayList<>();
            completers.add(new ArgumentCompleter(NullCompleter.INSTANCE
                                               , new OptionCompleter(NullCompleter.INSTANCE
                                                                   , this::commandOptions
                                                                   , 1)
                                                ));
            return completers;
        }

        private Set<String> capabilities() {
            return InfoCmp.getCapabilitiesByName().keySet();
        }

        private List<Completer> tputCompleter(String command) {
            List<Completer> completers = new ArrayList<>();
            completers.add(new ArgumentCompleter(NullCompleter.INSTANCE
                                               , new OptionCompleter(new StringsCompleter(this::capabilities)
                                                                   , this::commandOptions
                                                                   , 1)
                                                ));
            return completers;
        }

    }


    public static void main(String[] args) {
        String prompt = "groovy-repl> ";
        String rightPrompt = null;
        try {
            //
            // Parser & Terminal
            //
            DefaultParser parser = new DefaultParser();
            parser.setEofOnUnclosedBracket(Bracket.CURLY, Bracket.ROUND, Bracket.SQUARE);
            Terminal terminal = TerminalBuilder.builder().build();
            //
            // ScriptEngine and command registeries
            //
            GroovyEngine scriptEngine = new GroovyEngine();
            Builtins builtins = new Builtins(Paths.get(""), null, null);
            ConsoleEngine consoleEngine = new ConsoleEngineImpl(scriptEngine, parser, terminal, ()->Paths.get(""), null);
            MyCommands myCommands = new MyCommands();
            SystemRegistryImpl systemRegistry = new SystemRegistryImpl(consoleEngine, builtins, myCommands);
            systemRegistry.setTerminal(terminal);
            // systemRegistry.initialize(new File("./example/init.jline"));
            //
            //
            // LineReader
            //
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(systemRegistry.compileCompleters())
                    .parser(parser)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
                    .variable(LineReader.INDENTATION, 2)
                    .option(Option.INSERT_BRACKET, true)
                    .option(Option.EMPTY_WORD_OPTIONS, false)
                    .build();
            //
            // complete command registeries
            //
            builtins.setLineReader(reader);
            myCommands.setLineReader(reader);
            //
            // Tailtip widget
            //
            new TailTipWidgets(reader, systemRegistry::commandDescription, 5, TipType.COMPLETER);
            Map<String, KeyMap<Binding>>  keyMaps = reader.getKeyMaps();
            KeyMap<Binding> keyMap = keyMaps.get("main");
            keyMap.bind(new Reference("tailtip-toggle"), KeyMap.alt("s"));
            //
            // REPL-loop
            //
            consoleEngine.println(terminal.getName()+": "+terminal.getType());
            while (true) {
                try {
                    scriptEngine.del("_*");         // delete temporary variables
                    String line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
                    line = line.trim();
                    if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                        break;
                    }
                    ParsedLine pl = reader.getParser().parse(line, 0, ParseContext.ACCEPT_LINE);
                    Object result = systemRegistry.execute(pl);
                    consoleEngine.println(result);
                }
                catch (Options.HelpException e) {
                    Options.HelpException.highlight(e.getMessage(), Options.HelpException.defaultStyle()).print(terminal);
                }
                catch (UserInterruptException e) {
                    // Ignore
                }
                catch (EndOfFileException e) {
                    break;
                }
                catch (Exception e) {
                    consoleEngine.println(e);
                    scriptEngine.put("exception", e);  // save exception to console variable
                }
            }
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
