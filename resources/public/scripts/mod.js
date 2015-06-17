// https://github.com/SaleOkase/SaleOkase/wiki/Reports-API#pedir-una-lista-de-reportes

var QueueHandler = (function() {

    function initalize() {
        $.ajax({
            type: "GET",
            url: "api/reports",
            datatype: "json",
            success: function(response) {
                // TODO: Check that response array is not empty
                var content = response;
                var template = $("template").html();
                var result = Mustache.render(template, content);
                $("#content").html(result);
            },
            // TODO: Remove
            statusCode: {
                501: function () {
                    console.log("Not implemented!");
                }
            }
        });
    }

    // TODO
    function acceptReport() {
        // ...
    }

    // TODO
    function denyReport() {
        // ...
    }

    // TODO
    return {
        init: initalize()
        // ...
    }

}());

$(function() {

    QueueHandler.init;

    // TODO
    $(".accept").click(function() {
        // ...
    });

    // TODO
    $(".deny").click(function() {
       // ...
    });
});