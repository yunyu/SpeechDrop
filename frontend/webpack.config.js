const webpack = require('webpack');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

const config = {
    entry: {
        main: ['./src/js/main.js'],
        room: ['./src/js/room.js'],
        about: ['./src/js/about.js']
    },
    output: {
        filename: './dist/static/js/[name].js'
    },
    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        presets: ['env']
                    }
                }
            },
            {
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract({
                    fallback: 'style-loader',
                    use: ['css-loader', 'sass-loader']
                })
            }
        ]
    },
    plugins: [
        new webpack.optimize.CommonsChunkPlugin({
            name: 'commons',
            minChunks: 2
        }),
        new ExtractTextPlugin({
            filename: './dist/static/css/[name].css'
        })
    ],
    resolve: {
        alias: {
            vue$: 'vue/dist/vue.esm.js'
        }
    }
};

module.exports = config;
