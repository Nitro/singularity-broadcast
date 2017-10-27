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

  val AllSentences = Seq((":yoda-with-eyes:", Yoda), (":nitro:", TheNitroWay), (":doge:", Funny))

}
