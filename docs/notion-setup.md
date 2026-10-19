# Notion database setup

Steady reads one database shape: **one page per day, one checkbox property per habit.** This page
describes what the app needs and shows the database it was built against, so you can create your own
or adapt one you already have.

## What the app needs

| Property | Type | Required | What Steady does with it |
| --- | --- | --- | --- |
| The day | **Date** | Yes, one | Identifies which day a page is. Date only, no time. |
| Each habit | **Checkbox** | At least one | One habit per checkbox. The property name is the habit name, and a leading emoji becomes its icon. |
| Numbers to track | **Number** | No | Shown day by day on Week, as averages and as trends. A value typed in the app is written to its day's page. |
| Anything else | Any type | No | Ignored and left exactly as it is. |

Property **names are yours to choose**. During setup you tell the app which date is the day, which
checkboxes are habits and which numbers to track. That mapping is stored by Notion's property ID,
so renaming a column in Notion later does not break sync.

Steady never changes the database's structure. To add, rename or remove a habit, edit the checkbox
property in Notion, then pick it in **Settings → Your habits → Choose which columns are habits**.

## The example database

This is the database Steady was developed against. Recreate it as shown, or use it as a starting
point.

| Property | Type | Role in the app |
| --- | --- | --- |
| *(title)* | Title | Not used |
| `Date` | Date | The day |
| `📖 Read` | Checkbox | Habit |
| `✒️ Journal` | Checkbox | Habit |
| `📜 Quote` | Checkbox | Habit |
| `🥕 Diet` | Checkbox | Habit |
| `🎯 Focus` | Checkbox | Habit |
| `Weight` | Number | Optional number (kg) |
| `Sleep` | Number | Optional number (decimal hours, so `7.5` is 7 h 30 min) |
| `Area` | Multi-select | Not used, preserved |
| `Progress` | Formula | Not used (formulas are read-only in the Notion API) |

`Area` options: `AI Integration` · `IT Security` · `Photography` · `Drawing` · `Japanese` ·
`Finance` · `Notion` · `Study`

`Progress` is the share of the five habits done that day:

```
(toNumber(📖 Read) + toNumber(✒️ Journal) + toNumber(📜 Quote) + toNumber(🥕 Diet) + toNumber(🎯 Focus)) / 5
```

The app computes its own progress and never reads or writes this property, so you can leave it out.

## Creating it

1. In Notion, create a new database (a table view is the easiest to work with).
2. Add a **Date** property. This is the day key.
3. Add one **Checkbox** property per habit. Start the name with an emoji if you want an icon.
4. Optionally add **Number** properties for anything you measure daily.
5. Add one page per day and set its date. You do not need to backfill: when you check a habit off on
   a day that has no page yet, the app creates that day's page for you.

Then share the database with your Notion connection and connect the app, as described in the
[README](../README.md#setup).

## A database built another way

A database with one page per habit, one page per habit-and-day, or two related databases will
connect, but its properties will not map onto this model. The app does not detect that case yet.
