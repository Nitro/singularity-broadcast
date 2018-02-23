package util

import scala.util.Random

object QuoteRandom {

  def nextQuote() = {
    val (icon, sentences) = AllSentences(Random.nextInt(AllSentences.size))
    (icon, sentences(Random.nextInt(sentences.size)))
  }

  val Yoda = Seq(
    "Great execution. A bad idea it will not save. Think Jabba in lipstick.",
    "A great goal perfection is. But demand it always and ship you never will",
    "Solve business problems by solving user problems you should hmmmhm.",
    "The behaviour you observe is the behaviour you create. Hmmmh.",
    "Only good a solution, if real the problem is"
  ).map { text => s"Yoda says: $text" }

  val TheNitroWay = Seq(
    "Performance and results always come first",
    "We don’t tolerate bullshit",
    "We won’t lose our Aussie roots",
    "We take our work seriously, but not ourselves (Work Smart, Play Hard)",
    "We won’t work in silos. Every team is critical to success",
    "We don’t feel entitled. We appreciate everything we have",
    "Nitronauts are mates! We trust each other and have each other’s back",
    "We treat people with respect. Always",
    "We work extremely hard, but we do not allow burnout",
    "We leave egos and baggage at the door",
    "We take initiative and focus on the solution, not the problem. We communicate and collaborate effectively to get the right outcome",
    "No Fuckwits!",
    "We recruit and develop talent to the highest standards",
    "Our Nitronauts want to be here",
    "We will not do anything that damages our team or our company"
  ).map { text => s"Remember the Nitro Way: $text" }

  val Funny = Seq(
    "The best thing about a boolean is even if you are wrong, you are only off by a bit",
    "Without requirements or design, programming is the art of adding bugs to an empty text file",
    "Before software can be reusable it first has to be usable",
    "There are two ways to write error-free programs; only the third one works.",
    "It’s not a bug – it’s an undocumented feature",
    "A good programmer is someone who always looks both ways before crossing a one-way street",
    "Always code as if the guy who ends up maintaining your code will be a violent psychopath who knows where you live",
    "Truth can only be found in one place: the code",
    "Don’t comment bad code—rewrite it.",
    "If it is not written down, it does not exist",
    "Life doesn't have a ctrl-z. Type wisely.",
    "Life would be much easier if I had the source code.",
    "Any fool can write code that a computer can understand. Good programmers write code that humans can understand"
  ).map { text => s"Remember: $text" }

  val ChuckNorris = Seq(
    "When Chuck Norris throws exceptions, it's across the room",
    "All arrays Chuck Norris declares are of infinite size, because Chuck Norris knows no bounds",
    "Chuck Norris writes code that optimizes itself",
    "Check Norris doesn't test for equality because he has no equal",
    "Chuck Norris's first program was kill -9",
    "All browsers support the hex definition #chuck and #norris for the colors black and blue",
    "Chuck Norris can write infinite recursion function...and have them return",
    "Chuck Norris can solve the Towers of Hanoi in one move",
    "The only pattern Chuck Norris knows is the God Object",
    "Project managers never ask Chuck Norris for estimations...ever",
    "Chuck Norris doesn’t use web standards as the web will conform to him",
    "\"It works on my machine\" always holds true for Chuck Norris",
    "Chuck Norris doesn’t do Burn Down charts, he does Smack Down charts",
    "Chuck Norris can delete the Recycling Bin",
    "Chuck Norris can unit test entire applications with a single assert",
    "Chuck Norris doesn’t bug hunt as that signifies a probability of failure, he goes bug killing",
    "Chuck Norris’s keyboard doesn’t have a Ctrl key because nothing controls Chuck Norris",
    "Chuck Norris can divide by 0",
    "Chuck Norris’ keyboard has 2 keys: 0 and 1",
    "Chuck Norris knows the last digit of PI",
    "There is no Esc key on Chuck Norris’ keyboard, because no one escapes Chuck Norris",
    "There is only one person standing in a Chuck Norris scrum meeting"
  )

  val AllSentences = Seq(
    (":yoda-with-eyes:", Yoda),
    (":nitro:", TheNitroWay),
    (":doge:", Funny),
    (":chucknorris:", ChuckNorris)
  )

}
