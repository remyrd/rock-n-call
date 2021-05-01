# Rock n Call

![](/screenshot.png)
<sub>[background source](https://unsplash.com/photos/iQjVXncVfbg)</sub>

Export oncall information from Pagerduty into Excel format.

I started this project as a weekend thing while working at my former employer. We (the engineering teams) spent several hours producing these reports, so I automated the whole thing.

The final product is an OSX application as this is what HR - the intended user - used. 

## Requirements

This application relies on the user posessing an API token. If you have an account in Pagerduty, generate your own Token and keep it in mind!

[Here is a guide on how to generate an api key (token)](https://support.pagerduty.com/docs/generating-api-keys#generating-a-personal-rest-api-key)

## Installation

Check the Releases and download/install the latest disk image.

Copy the contents of the disk image to your **Applications**.

Run the application. Look for `rock-n-call-ui`. :warning: the first run will fail, this is expected.

Go to _System Preferences_ / _Security & Privacy_ / _General_ (the tab on top)

You'll notice a section that mentions you tried to open an unsecure app. Allow it.

Open the app again from the Applications menu.

## Features

- Per-team sheet generation
- Czech Rep. :czech_republic: national holidays taken into account
- Pagerduty schedule overrides
- Parameter saved and persisted
  - Token
  - Output directory
  - Employee information (Manager, Personal No.)
  - Team information (Employees, Escalation Policy, Recent files)
  - Latest generated documents

## Usage

On the first run, you'll need to provide your API token (see [requirements](#Requirements)). 

The application remembers everything you type in, so you fill in the information only once. Next time you start the app,
the fields will be already filled out.

1. Copy/paste your token into the form and press `START`. A dropdown menu with a list of available teams will appear.
2. Select a team and give it a few seconds to load. If there are multiple escalation policies you'll have to choose one.
3. After choosing a team, a list of names will appear. Check the ones that qualify for oncall payout.
4. For each checked name, fill in their *Personal Number* and *Manager*.
5. Press **Generate Sheet** to create an excel sheet with the detailed schedule for this team.
6. A blue link to the excel sheet is now visible under *Recent Documents*. Click on the link to open the Excel file.
7. At any time, you can change the directory where new sheets are saved to by clicking on *Change directory*.

### The Configuration file

The application will save its configuration in your home folder under a file called `.rock-n-call`.

It is saved in your `HOME` folder. Whatever information is in there the application will read when starting up.

You can edit this file if you wish to. It uses the [EDN format](https://github.com/edn-format/edn).

## Development

[Install the Clojure CLI](https://clojure.org/guides/getting_started).
[Learn how to use the dependencies and the developer environment](https://clojure.org/guides/deps_and_cli)

You can generate a fat jar by running
```
clojure -X:depstar && java -jar dist/ui.jar
```

You can also go the Clojure way and invoke the `rock-n-call.ui.core/start!` function from a repl.
