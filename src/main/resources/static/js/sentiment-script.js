window.addEventListener('load', function(){
    //Vue.js element
    const vue = new Vue({
        el: "#app",
        data: function(){
            return{
                //counter number of offensive tweets
                offensive: 0,
                //counter number of offensive tweets
                nonOffensive: 0,
                //pie chart time selection value
                selectPieTime: 'all',
                //Pie chart time selection values
                times: [
                    {text: "Letzter Tag", value: "day"}, 
                    {text: "Letzte Woche", value: "week"}, 
                    {text: "Letzter Monat", value: "month"},
                    {text: "Gewählter Zeitraum", value: "range"},
                ],
                //datepicker selection, initialized with one week
                selectedDate: {
                    start:  getDate(-7),
                    end:  getDate(0)
                },
                hashtags: [],

                popularHashtags: [],

            }
        },
        methods:{

            addHashtag: function () {
                let tag =  $('#newhashtag').val();
                if (tag.substr(0,1) !== "#") tag = '#' + tag;
                this.hashtags.push({value: tag});
                $('#newhashtag').val('');
                },

            updateHashtags: function () {
                axios.post('/sentiment19/popularhashtags?limit=8',{})
                    .then(response => this.popularHashtags = response.data.hashtags.map(function (tag, index) {
                        return {value: tag.hashtag, key: index, popular: 1, hidden: 0, count: tag.count,
                            percent: (tag.count / response.data.total * 100).toFixed(2) + '%'
                        };
                    }))

                this.popularHashtags.forEach(function (e) {
                    if (this.hashtags.filter(tag => (tag.value === e.value)).length > 0) {
                        e.hidden = 1;
                    }
                })

            },
            /**
             * Updates the main offensive and nonOffensive tweet amount counters (and initiates Pie Chart update)
             */
            updateCounters: function(){

                axios.all([
                    axios.post('/sentiment19/stats',{offensive: 1}),
                    axios.post('/sentiment19/stats',{offensive: 0})
                  ])
                  .then(axios.spread((off, nonOff) => {
                    this.offensive = off.data.count
                    this.nonOffensive = nonOff.data.count
                    this.updatePieChart()
                  }));
                
            },
            /**
             * Updates pie chart, based on selected time frame
             */
            updatePieChart: function(){

                //local function to format given date to match the required format for backend request
                function stringDate(d){
                    return d.getFullYear() + "-" + (d.getMonth()+1) + "-" + d.getDate()
                }

                let data
                let startDate = new Date()
                let endDate = new Date();

                //if selected time frame is not further specified, counts from entire period are used (value the same as the counters)
                if(this.selectPieTime === ''){
                    data = [this.offensive, this.nonOffensive]
                    pieChart.data.datasets[0].data = data
                    pieChart.update()
                //Request data for chosen time frame from backend
                } else {
                    if(this.selectPieTime === 'day'){
                        startDate = getDate(-1)
                    }else if(this.selectPieTime === 'week'){
                        startDate = getDate(-7)
                    }else if(this.selectPieTime === 'month'){
                        startDate = getDate(-30)
                    }else if(this.selectPieTime === 'range'){
                        endDate = this.selectedDate.end
                        startDate = this.selectedDate.start;
                        console.log('there')
                    } else {
                        console.log('here')
                        startDate = null;
                        endDate = null;
                    }
                    axios.all([
                        axios.post('/sentiment19/stats',{offensive: 1, start: startDate,  end: endDate, hashtags: this.hashtags.map(function (o) {
                                    return o.value
                                })}),
                        axios.post('/sentiment19/stats',
                        {offensive: 0, start: startDate,  end: endDate, hashtags: this.hashtags.map(function (o) {
                                    return o.value
                                })})
                      ])
                      .then(axios.spread((off, nonOff) => {
                        data =  [off.data.count, nonOff.data.count]
                        pieChart.data.datasets[0].data = data
                        pieChart.update()
                    }));
                }
            },

            //Update the line chart labels based on the selected start-/enddate
            //To-Do: Change the dataset aswell -> has to be requested from backend
            updateLineChart: function(){
                axios.all([
                    axios.get('/sentiment19/timeline',
                        {params: {offensive: 1, startdate: this.selectedDate.start, enddate: this.selectedDate.end}}),
                    axios.get('/sentiment19/timeline',
                        {params: {offensive: 0, startdate: this.selectedDate.start, enddate: this.selectedDate.end}})
                ])
                    .then(axios.spread((off, nonOff) => {
                        dateRange = getRangeOfDates(moment(this.selectedDate.start), moment(this.selectedDate.end), 'day')
                        lineChart.data.labels = dateRange
                        lineChart.data.datasets[0].data = off.data.timeline
                        lineChart.data.datasets[1].data = nonOff.data.timeline
                        lineChart.update()
                        if (this.selectPieTime == 'range') {
                            vue.updatePieChart();
                        }
                    }));

            },
        },
        updated: function () {
            $(function(){
                $('[data-toggle="tooltip"]').tooltip({ trigger: "hover", html:true});
                $('.tooltip').remove();
            })
        }
    })

    //Used Chart.js as it seemed easier (compared to D3.js) to quickly implement the graphs we need (atleast for now)

    //Pie chart for direct comparison off vs. nonOff tweet amount
    var ctx2 = document.getElementById('pieChart').getContext('2d');
    var pieChart = new Chart(ctx2, {
    // type of chart
    type: 'pie',

    //Data and style options for the line chart
    data: {
        labels: ['offensive', 'non-offensive'],
        datasets: [{
            backgroundColor: ['rgb(255, 99, 132)','rgb(0,255,0)'],
            borderColor: 'rgb(255,255,255)',
            fill: false,
            data: [vue.offensive, vue.nonOffensive]
        },
    ]
    },

    // Configuration options go here
    options: {
        rotation: 0.5 * Math.PI
    }
    });
    

    //Line chart - comparing offensive/nonOffensive over specified time
    var ctx2 = document.getElementById('lineChart').getContext('2d');
    var lineChart = new Chart(ctx2, {
    // type of chart
    type: 'line',

    //Data and style options for the line chart
    data: {
        labels: [''],
        datasets: [{
            label: 'offensive',
            backgroundColor: 'rgb(255, 99, 132)',
            borderColor: 'rgb(255, 99, 132)',
            fill: false,
            data: [1,2,3,4]
        },
        {
            label: 'non-offensive',
            backgroundColor: 'rgb(0, 255, 0)',
            borderColor: 'rgb(0, 255, 0)',
            fill: false,
            data: [5,6,7,8]
        },
    ]
    },

    // Configuration options go here
    options: {
        elements: {
            line: {
                tension: 0 // disables bezier curves
            }
        },
        scales: {
            yAxes: [{
                ticks:{
                    beginAtZero: true //y-Axis starts at 0
                }
            }]
        }
    }
    });

    /**
     * Initialise Tweet display, Tweet amount counters and the pie and line charts
     */
    function init(){
        displayTweet()
        vue.updateCounters()
        vue.updatePieChart()
        vue.updateLineChart()
        vue.updateHashtags()

    $(document).on('load', function () {
        $(function(){
            $('[data-toggle="tooltip"]').tooltip();
        })
    })
    }
    
   //run init function
    init()

});

/**
 * Formats a given date, to be displayed better as a line chart label
 * @param {*} date date to be formated
 * @returns formatted date
 */
function formatDate(date){
    return date.getDate() + "." + (date.getMonth()+1)  + "." + date.getFullYear().toString().slice(-2)
}

/**
 * Returns range of dates, between specified start and end date, with a given step length (key)
 * @param {*} start Start date for the date range
 * @param {*} end End date for the date range
 * @param {*} key Increment size to be used for each step
 * @param {*} arr Resulting array - does not need to be provided on function call
 * @returns (Already formmatted) date range
 */
function getRangeOfDates(start, end, key, arr = [start.startOf(key)]) {
  
    if(start.isAfter(end)) throw new Error('start must precede end')
    
    const next = moment(start).add(1, key).startOf(key);
    
    if(next.isAfter(end, key)) return arr.map((v) => formatDate(v.toDate()))
    
    return getRangeOfDates(next, end, key, arr.concat(next));
    
}

/**
 * Requests html code for offensive/nonoffensive Tweets and displays them as examples
 */
 function displayTweet(){

    axios.all([
        axios.get('/sentiment19/tweet',
        {params: {offensive: 1}}),
        axios.get('/sentiment19/tweet',
        {params: {offensive: 0}})
      ])
      .then(axios.spread((offTweet, nonOffTweet) => {
        document.getElementById("offTweet").innerHTML = offTweet.data.html
        document.getElementById("nonOffTweet").innerHTML = nonOffTweet.data.html
        twttr.widgets.load()
    }));
}

/**
 *Get today's date plus or minus a specified number of days (0 for today)
 *
 * @param {*} d  number of days to be added/subtracted of today
 * @returns today +/- specified number of days
 */
function getDate(d){
    if(d > 0){
        return moment().add(d,'days').toDate()
    }else if(d < 0){
        return moment().subtract(Math.abs(d), 'days').toDate()
    }else if(d === 0){
        return moment().toDate()
    }
}