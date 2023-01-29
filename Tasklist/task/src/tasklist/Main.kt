package tasklist

import kotlin.system.exitProcess
import kotlinx.datetime.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

const val FILENAME = "tasklist.json"
data class TaskJson (
    var date: String,
    var time: String,
    var priority: String,
    var overdue: String,
    var details: List<String>
)

fun main() {
    // write your code here
    Tasklist.restore(File(FILENAME))

    while (true) {
        println("Input an action (add, print, edit, delete, end):")
        when (readln()) {
            "add" -> Tasklist.add()
            "print" -> Tasklist.print()
            "delete" -> Tasklist.edit(true)
            "edit" -> Tasklist.edit()
            "end" -> println("Tasklist exiting!").also {

                val taskJson = Tasklist.convert()

                if (taskJson.isNotEmpty()) {
                    val jsonFile = File(FILENAME)
                    jsonFile.writeText(taskJson)
                }
                exitProcess(0)
            }
            else -> println("The input action is invalid")
        }
        continue
    }
}

object Tasklist {
    private var taskList = mutableListOf<Task>()
    private val adapter = getMoshi()

    data class Task(
        var dateTime: LocalDateTime,
        var priority: String,
        var overdue: String,
        var details: List<String>
    )

    private fun getMoshi(): JsonAdapter<List<TaskJson?>> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val types = Types.newParameterizedType(List::class.java, TaskJson::class.java)
        return moshi.adapter(types)
    }

    fun restore(file: File) {
        if (file.exists()) {
            val taskJson = adapter.fromJson(file.readText()) ?: listOf()
            for (task in taskJson) {
                val date = task?.date?.split("-")!!.map { it.toInt() }
                val time = task.time.split(":").map { it.toInt() }
                taskList.add(
                    Task(
                        dateTime = LocalDateTime(date[0], date[1], date[2], time[0], time[1]),
                        priority = task.priority,
                        overdue = task.overdue,
                        details = task.details
                    ))
            }
        }
    }

    fun convert(): String {
        val taskListJson = mutableListOf<TaskJson>()
        for (task in taskList) {
            taskListJson.add(
                TaskJson(
                    date = task.dateTime.date.toString(),
                    time = "${task.dateTime.hour}:${task.dateTime.minute}",
                    priority = task.priority,
                    overdue = task.overdue,
                    details = task.details
                ))
        }
        return adapter.toJson(taskListJson)
    }

    fun print() {
        var i = 0
        if (taskList.isEmpty()) println("No tasks have been input")
        else {
            val header = """
                +----+------------+-------+---+---+--------------------------------------------+
                | N  |    Date    | Time  | P | D |                   Task                     |
                +----+------------+-------+---+---+--------------------------------------------+
            """.trimIndent()
            println(header)

            for (task in taskList) {
                for ((j, t) in task.details.withIndex()) {
                    if (j == 0) {
                        println("| %-2s | %d-%02d-%02d | %02d:%02d | %s | %s |%s%${44-t.length + 1}s".format(
                            ++i,
                            task.dateTime.year,
                            task.dateTime.monthNumber,
                            task.dateTime.dayOfMonth,
                            task.dateTime.hour,
                            task.dateTime.minute,
                            task.priority,
                            task.overdue,
                            t,
                            '|'
                        ))
                    } else {
                        println("|    |            |       |   |   |%s%${44-t.length + 1}s".format(t, "|"))
                    }
                }
                println("+----+------------+-------+---+---+--------------------------------------------+")
            }
        }
    }

    fun edit(delete: Boolean = false) {
        this.print()
        if (taskList.isEmpty()) return
        val taskNumber = askTaskNumber()
        if (delete) taskList.removeAt(taskNumber-1).also { println("The task is deleted") }.also { return }
        taskChange(taskList[taskNumber-1])
    }

    private fun taskChange(task: Task) {
        var editField: String
        val date: LocalDate

        while (true) {
            println("Input a field to edit (priority, date, time, task):").also { editField = readln() }

            if (editField !in listOf("priority", "date", "time", "task")) {
                println("Invalid field")
                continue
            }

            when (editField) {
                "priority" -> task.priority = askPriority()
                "date" -> {
                    date = askDate()
                    task.dateTime = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, task.dateTime.hour, task.dateTime.minute)
                }
                "time" -> task.dateTime = askTime(LocalDate(task.dateTime.year, task.dateTime.monthNumber, task.dateTime.dayOfMonth))
                else -> task.details = askDetails()
            }
            println("The task is changed")
            break
        }
    }

    private fun askTaskNumber(): Int {
        var taskNumber = -1
        while (true) {
            println("Input the task number (1-${taskList.size}):")
            try { taskNumber = readln().toInt() } catch (_: NumberFormatException) {}

            if (taskNumber !in 1..taskList.size) {
                println("Invalid task number")
                continue
            } else break
        }
        return taskNumber
    }

    fun add() {
        val priority = askPriority()
        val date = askDate()
        val dateTime = askTime(date)
        val details = askDetails()
        val overdue = calcOverdue(date)

        taskList.add(Task(dateTime, priority, overdue, details))
        //taskList.add(Task(dateTime, priority, overdue, details))
    }

    private fun askDetails(): List<String> {
        val taskList = mutableListOf<String>()

        println("Input a new task (enter a blank line to end):")
        while (true) {

            val input = readln()

            if (input == "") break
            else if (input.all { it == ' ' }) {
                println("The task is blank")
                break
            }
            val inputList = input.trim { it == '\t' || it == '\n' || it == ' ' }.chunked(44)
            taskList.addAll(inputList)
        }
        return taskList
    }

    private fun calcOverdue(date: LocalDate): String {
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date

        return if (currentDate.daysUntil(date) > 0) "\u001B[102m \u001B[0m" //"I"
        else if (currentDate.daysUntil(date) < 0) "\u001B[101m \u001B[0m"   //"O"
        else "\u001B[103m \u001B[0m"  //"T"
    }

    private fun askPriority(): String {
        var priority: String
        while (true) {
            println("Input the task priority (C, H, N, L):")
            priority = readln().uppercase()
            if (priority in listOf("C", "H", "N", "L")) break
        }
        return when (priority) {
            "C" -> "\u001B[101m \u001B[0m"
            "H" -> "\u001B[103m \u001B[0m"
            "N" -> "\u001B[102m \u001B[0m"
            else -> "\u001B[104m \u001B[0m"
        }
    }

    private fun askDate(): LocalDate {
        var date: LocalDate
        while (true) {
            println("Input the date (yyyy-mm-dd):")
            val dateIn = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")
                .find(readln())?.value?.split("-")?.map { it.toInt() } ?: listOf(0, 0, 0)
            try {
                date = LocalDate(dateIn[0], dateIn[1], dateIn[2])
                break
            } catch (err: IllegalArgumentException) {
                println("The input date is invalid")
                continue
            }
        }
        return date
    }

    private fun askTime(date: LocalDate): LocalDateTime {
        var dateTime: LocalDateTime
        while (true) {
            println("Input the time (hh:mm):")
            val timeIn = Regex("\\d{1,2}:\\d{1,2}").find(readln())?.value?.split(":")?.map { it.toInt() } ?: listOf(24, 60)
            try {
                dateTime = LocalDateTime(date.year, date.month, date.dayOfMonth, timeIn[0], timeIn[1])
                break
            } catch (err: IllegalArgumentException) {
                println("The input time is invalid")
                continue
            }
        }
        return dateTime
    }
}
