jQuery(function ($) {
    var watchedFields = ["projectKey", "clientId", "clientSecret"];
    var prefix = "credentials-";

    $('.raml-console-sidebar-oauth-scopes input').livequery(function() {
        $(this).change(function() {
            $('.raml-console-sidebar-oauth-scopes input').each(function (idx, cb) {
                localStorage.setItem(prefix + "commercetools-scopes-" + idx, $(cb).is(':checked'));
            })
        });
    });

    $('input[name=projectKey]').livequery(function() {
        var $scope = angular.element($(".raml-console-sidebar-oauth-scopes").get()).scope();

        var rememberFields = function (prefix, fields) {
            fields.forEach(function (f) {
                $('input[name=' + f + ']').on('input', function() {
                    localStorage.setItem(prefix + f, $(this).val());
                });
            })
        };

        var restoreFields = function (prefix, fields) {
            fields.forEach(function (f) {
                var input = $('input[name=' + f + ']');
                var fieldValue = localStorage.getItem(prefix + f);
                if (fieldValue) {
                    input.val(fieldValue);
                    input.trigger('input');
                }
            })
        };

        var setupScopeUpdater = function (prefix, field) {
            $('input[name=' + field + ']').on('input', function() {
                var val = $(this).val();

                $scope.$apply(function() {
                    for (var i = 0; i < $scope.scopes.length; i++) {
                        $scope.scopes[i] = $scope.scopes[i].replace(/:.*$/, ":" + val);
                        $scope.scopes[i] = $scope.scopes[i].replace(/:$/, ":{projectKey}");
                    }
                });
            });
        };

        var restoreScopes = function () {
            $('.raml-console-sidebar-oauth-scopes input').each(function (idx, cb) {
                var checked = localStorage.getItem(prefix + "commercetools-scopes-" + idx) == 'true';

                if (checked) {
                    $(cb).trigger('click'); // robot clicking checkboxes :)
                }
            })
        };

        setupScopeUpdater(prefix, watchedFields[0])
        restoreFields(prefix, watchedFields)
        rememberFields(prefix, watchedFields)
        restoreScopes();
    });
}(jQuery));
